// Read-only production smoke check; login issues tokens but does not place orders or change campaigns.
const fs = require('node:fs');
const assert = require('node:assert/strict');
const base = 'https://pawconnect-ute.up.railway.app';
const password = process.env.COUPON_TEST_PASSWORD || fs.readFileSync('src/main/resources/application.properties', 'utf8').match(/^app\.seed\.users\.password=(.+)$/m)?.[1].trim();
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const checks = [];
async function request(route, token, method = 'GET', body) {
    const r = await fetch(base + route, { method, signal: AbortSignal.timeout(20000),
        headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
        ...(body ? { body: JSON.stringify(body) } : {}) });
    let data; try { data = await r.json(); } catch { data = null; }
    return { status: r.status, data };
}
async function login(email) {
    const result = await request('/api/auth/login', null, 'POST', { usernameOrEmail: email, password });
    assert.equal(result.status, 200, 'Demo account login');
    assert(result.data.accessToken, 'Missing access token'); return result.data.accessToken;
}
(async () => {
    let deployed = false;
    for (let attempt = 0; attempt < 30; attempt++) {
        try {
            const response = await fetch(base + '/checkout?release=coupons-20261029', { signal: AbortSignal.timeout(15000) });
            if (response.ok && (await response.text()).includes('id="checkout-coupons"')) { deployed = true; break; }
        } catch { }
        console.log('Waiting for Railway coupon deployment (' + (attempt + 1) + '/30)');
        await delay(15000);
    }
    assert(deployed, 'Railway has not served the new checkout within the polling window.');
    checks.push('Public checkout serves the coupon release');
    const admin = await login('admin@pawconnect.example');
    const customer = await login('an@pawconnect.example');
    const manager = await login('manager.hcm@pawconnect.example');
    checks.push('Admin, customer and branch manager can still log in');
    const list = await request('/api/admin/coupons', admin);
    assert.equal(list.status, 200);
    const expected = { DON500K: ['PERCENT', 10], LANDAU15: ['PERCENT', 15], SPA50K: ['FIXED', 50000] };
    for (const [code, [type, value]] of Object.entries(expected)) {
        const coupon = list.data.find(c => c.code === code);
        assert(coupon, 'Missing built-in coupon ' + code); assert.equal(coupon.discountType, type);
        assert.equal(Number(coupon.discountValue), value); assert.equal(coupon.usesPerCustomer, 1);
    }
    checks.push('Three default campaigns persisted on Railway MySQL with correct amounts and personal limits');
    assert.equal((await request('/api/admin/coupons', null)).status, 401);
    assert.equal((await request('/api/admin/coupons', customer)).status, 403);
    assert.equal((await request('/api/admin/coupons', manager)).status, 403);
    checks.push('Unauthenticated/customer/manager admin-coupon access rejected');
    const ownOrders = await request('/api/orders/my', customer); assert.equal(ownOrders.status, 200);
    for (const order of ownOrders.data) {
        assert(Number(order.totalAmount) >= 0);
        assert.equal(Number(order.subtotalAmount) - Number(order.discountAmount), Number(order.totalAmount));
    }
    checks.push('Existing customer orders read successfully with consistent totals');
    const cart = await request('/api/cart', customer); assert.equal(cart.status, 200);
    if (cart.data.items?.length) {
        const firstBranch = cart.data.items[0].branchId;
        const itemIds = cart.data.items.filter(i => i.branchId === firstBranch).map(i => i.id);
        const quote = await request('/api/coupons/quote', customer, 'POST', { branchId: firstBranch, itemIds, couponId: null });
        assert.equal(quote.status, 200); assert(quote.data.coupons.length >= 3);
        assert.equal(Number(quote.data.subtotal), Number(quote.data.totalAmount));
        for (const c of quote.data.coupons) assert(c.remaining >= 0 && c.remaining <= 100);
        const recommended = quote.data.recommendedCouponId;
        if (recommended != null) {
            const discounted = await request('/api/coupons/quote', customer, 'POST', { branchId: firstBranch, itemIds, couponId: recommended });
            assert.equal(discounted.status, 200);
            assert.equal(Number(discounted.data.subtotal) - Number(discounted.data.discountAmount), Number(discounted.data.totalAmount));
        }
        checks.push('Live cart quote/eligibility/recommendation computed correctly without consuming a coupon');
    } else {
        const quote = await request('/api/coupons/quote', customer, 'POST', { branchId: 1, couponId: null });
        assert.equal(quote.status, 409); checks.push('Empty-cart quote safely rejected; no production cart/order added for testing');
    }
    const adminHtml = await fetch(base + '/admin/coupons', { signal: AbortSignal.timeout(20000) });
    assert.equal(adminHtml.status, 200); assert((await adminHtml.text()).includes('id="coupon-form"'));
    checks.push('Public admin coupon screen served');
    fs.mkdirSync('target/coupon-ui', { recursive: true });
    const report = { passed: true, checkedAt: new Date().toISOString(), productionBusinessWrites: false, checks };
    fs.writeFileSync('target/coupon-ui/live-results.json', JSON.stringify(report, null, 2));
    console.log(JSON.stringify({ passed: true, checks: checks.length, productionBusinessWrites: false }));
})().catch(error => { console.error(error.message); process.exitCode = 1; });
