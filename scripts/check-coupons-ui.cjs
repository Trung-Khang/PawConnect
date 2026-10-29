// Local-only real Chrome smoke test. Artifacts contain no passwords or tokens.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const base = process.env.COUPON_TEST_URL || 'http://127.0.0.1:18090';
assert(['127.0.0.1', 'localhost'].includes(new URL(base).hostname), 'Run only on a disposable local database.');
const output = path.resolve('target/coupon-ui'); fs.mkdirSync(output, { recursive: true });
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const password = process.env.COUPON_TEST_PASSWORD || fs.readFileSync('src/main/resources/application.properties', 'utf8').match(/^app\.seed\.users\.password=(.+)$/m)?.[1].trim();
const checks = []; const exceptions = []; const pending = new Map(); let nextId = 1; let ws;
async function api(url, token, method = 'GET', body) {
    const response = await fetch(base + url, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
    const data = await response.json(); assert(response.ok, `${method} ${url}: ${response.status}`); return data;
}
async function login(email) { return (await api('/api/auth/login', null, 'POST', { usernameOrEmail: email, password })).accessToken; }
function send(method, params = {}, sessionId) {
    return new Promise((resolve, reject) => { const id = nextId++; pending.set(id, [resolve, reject]); ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) })); });
}
async function page(token, route) {
    const { browserContextId } = await send('Target.createBrowserContext');
    const { targetId } = await send('Target.createTarget', { url: 'about:blank', browserContextId });
    const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
    const command = (method, params) => send(method, params, sessionId);
    const evaluate = async expression => {
        const result = await command('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
        if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
        return result.result.value;
    };
    const until = async expression => { for (let i = 0; i < 120; i++) { if (await evaluate(expression).catch(() => false)) return; await wait(200); } throw new Error('Timeout waiting for UI: ' + expression); };
    await command('Page.enable'); await command('Runtime.enable'); await command('Network.enable');
    await command('Page.navigate', { url: base + '/shop' }); await until('document.readyState === "complete"');
    await evaluate(`localStorage.setItem('pawconnect.accessToken',${JSON.stringify(token)})`);
    await command('Page.navigate', { url: base + route }); await until('document.readyState === "complete"');
    return { command, evaluate, until };
}
async function capture(p, name) {
    await p.evaluate('window.scrollTo(0,0)');
    await wait(350);
    const { cssContentSize } = await p.command('Page.getLayoutMetrics');
    const result = await p.command('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true,
        clip: { x: 0, y: 0, width: cssContentSize.width, height: cssContentSize.height, scale: 1 } });
    fs.writeFileSync(path.join(output, name + '.png'), Buffer.from(result.data, 'base64'));
}

(async () => {
    // Embedded Tomcat may bind before the seed runners finish; wait for a usable local account.
    let adminReady = null;
    for (let attempt = 0; attempt < 90; attempt++) {
        try { adminReady = await login('admin@pawconnect.example'); break; }
        catch { await wait(1000); }
    }
    assert(adminReady, 'Local demo server/seed account not ready.');
    const chromePath = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
    const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'paw-coupon-chrome-'));
    const browser = spawn(chromePath, ['--headless=new', '--disable-gpu', '--no-first-run', '--remote-debugging-port=19449', '--remote-allow-origins=*', '--user-data-dir=' + profile, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
    try {
        let version;
        for (let i = 0; i < 100; i++) { try { version = await (await fetch('http://127.0.0.1:19449/json/version')).json(); break; } catch { await wait(200); } }
        assert(version, 'Chrome not available');
        ws = new WebSocket(version.webSocketDebuggerUrl); await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
        ws.onmessage = event => {
            const m = JSON.parse(event.data);
            if (m.id) { const handlers = pending.get(m.id); if (handlers) { pending.delete(m.id); m.error ? handlers[1](new Error(m.error.message)) : handlers[0](m.result); } }
            else if (m.method === 'Runtime.exceptionThrown') exceptions.push(m.params.exceptionDetails.text);
        };
        const admin = adminReady; const customer = await login('an@pawconnect.example');
        const branches = await api('/api/branches'); const categories = await api('/api/categories');
        const product = await api('/api/products', admin, 'POST', { name: 'UI coupon test product', price: 600000, stock: 50, branchId: branches[0].id, categoryId: categories[0].id });
        await api('/api/cart/items?productId=' + product.id + '&quantity=1', customer, 'POST');
        const customerPage = await page(customer, '/checkout');
        await customerPage.until('document.querySelectorAll("#checkout-coupons input").length === 3 && !document.querySelector("#confirm-order").disabled');
        const initial = await customerPage.evaluate(`({count:document.querySelectorAll('#checkout-coupons input:checked').length,total:document.querySelector('#checkout-total').textContent,disabled:document.querySelectorAll('#checkout-coupons input:disabled').length})`);
        assert.equal(initial.count, 1); assert(initial.total.includes('510.000')); assert.equal(initial.disabled, 1);
        checks.push('Default 15% recommendation and unearned spa disabled');
        await customerPage.evaluate(`document.querySelectorAll('#checkout-coupons input')[0].click()`);
        await customerPage.until(`!document.querySelector('#confirm-order').disabled && document.querySelector('#checkout-total').textContent.includes('540.000')`);
        assert.equal(await customerPage.evaluate(`document.querySelectorAll('#checkout-coupons input:checked').length`), 1);
        await customerPage.evaluate(`document.querySelectorAll('#checkout-coupons input')[0].click()`);
        await customerPage.until(`!document.querySelector('#confirm-order').disabled && document.querySelector('#checkout-total').textContent.includes('600.000')`);
        assert.equal(await customerPage.evaluate(`document.querySelectorAll('#checkout-coupons input:checked').length`), 0);
        checks.push('Selecting a different code replaces it; unchecking restores original price');
        await customerPage.evaluate(`document.querySelectorAll('#checkout-coupons input')[1].click()`);
        await customerPage.until(`!document.querySelector('#confirm-order').disabled && document.querySelector('#checkout-total').textContent.includes('510.000')`);
        const adminPage = await page(admin, '/admin/coupons');
        await adminPage.until('document.querySelectorAll(".coupon-admin-item").length === 3');
        for (const width of [320, 375, 390, 768, 1024, 1440]) {
            for (const [role, p, target] of [['checkout', customerPage, '#checkout-coupons'], ['admin', adminPage, '#coupon-form']]) {
                await p.command('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: width < 768 });
                await wait(250);
                const metrics = await p.evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(target)}),r=e.getBoundingClientRect();return {inner:innerWidth,page:document.documentElement.scrollWidth,left:r.left,right:r.right,width:r.width}})()`);
                assert(metrics.page <= metrics.inner + 1, `${role} page overflows at ${width}: ${JSON.stringify(metrics)}`);
                assert(metrics.left >= -1 && metrics.right <= metrics.inner + 1, `${role} controls overflow at ${width}`);
                if ([390, 1440].includes(width)) { await p.evaluate(`document.querySelector(${JSON.stringify(target)}).scrollIntoView({block:'center'})`); await capture(p, `${role}-${width}`); }
                checks.push(`${role} layout ${width}px: no horizontal overflow`);
            }
        }
        await customerPage.evaluate(`document.querySelector('#recipient-name').value='Khách kiểm thử';document.querySelector('#recipient-phone').value='0901234567';document.querySelector('#delivery-address').value='Địa chỉ thử';document.querySelector('#checkout-form').requestSubmit()`);
        await customerPage.until('document.querySelector("#checkout-confirm-dialog").open');
        assert.equal(await customerPage.evaluate(`document.querySelector('#confirm-amount').textContent`), await customerPage.evaluate(`document.querySelector('#checkout-total').textContent`));
        await customerPage.command('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
        await capture(customerPage, 'confirmation-mobile');
        await customerPage.evaluate(`document.querySelector('#checkout-confirm-submit').click()`);
        await customerPage.until('location.pathname.includes("/receipt") && !document.querySelector("#receipt").hidden');
        assert((await customerPage.evaluate(`document.querySelector('#receipt-discount').textContent`)).includes('90.000'));
        assert((await customerPage.evaluate(`document.querySelector('#receipt-total').textContent`)).includes('510.000'));
        await customerPage.evaluate(`document.querySelector('#receipt-discount-summary').scrollIntoView({block:'center'})`);
        await capture(customerPage, 'receipt-mobile'); checks.push('Real UI checkout confirms discounted total and receipt displays stored discount');
        await api('/api/cart/items?productId=' + product.id + '&quantity=1', customer, 'POST');
        await customerPage.command('Page.navigate', { url: base + '/checkout' });
        await customerPage.until('document.querySelectorAll("#checkout-coupons input").length === 3 && !document.querySelector("#confirm-order").disabled');
        assert((await customerPage.evaluate(`document.querySelectorAll('.coupon-choice')[1].textContent`)).includes('Còn 0 lượt'));
        assert(await customerPage.evaluate(`document.querySelectorAll('#checkout-coupons input')[1].disabled`));
        checks.push('Next checkout shows first-purchase coupon exhausted and disabled');
        await adminPage.evaluate(`document.querySelector('#coupon-code').value='UI20';document.querySelector('#coupon-name').value='Ưu đãi kiểm thử giao diện';document.querySelector('#coupon-value').value='20';document.querySelector('#coupon-uses').value='2';document.querySelector('#coupon-form').requestSubmit()`);
        await adminPage.until('document.querySelectorAll(".coupon-admin-item").length === 4');
        await adminPage.evaluate(`Array.from(document.querySelectorAll('.coupon-admin-item')).find(e=>e.textContent.includes('UI20')).querySelector('button').click()`);
        await adminPage.until(`Array.from(document.querySelectorAll('.coupon-admin-item')).some(e=>e.textContent.includes('UI20')&&e.textContent.includes('Tạm ngưng')&&e.querySelector('button').textContent==='Kích hoạt')`);
        checks.push('Admin creates a campaign and disables it through real UI');
        assert.equal(exceptions.length, 0, 'Uncaught browser JavaScript errors');
        fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify({ passed: true, checks, uncaughtErrors: exceptions, testedAt: new Date().toISOString() }, null, 2));
        console.log(JSON.stringify({ passed: true, checks: checks.length, artifacts: output }));
    } finally { if (ws?.readyState === 1) { await send('Browser.close').catch(() => {}); ws.close(); } browser.kill(); }
})().catch(error => { fs.writeFileSync(path.join(output, 'failure.txt'), error.message); console.error(error.message); process.exitCode = 1; });
