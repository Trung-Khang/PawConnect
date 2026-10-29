(() => {
    const token = localStorage.getItem('pawconnect.accessToken');
    let branchId = null;
    let confirmationKey = null;
    let items = [];
    let selectedCouponId = null;
    let quoteSequence = 0;
    let quoteReady = false;
    let hasSuggested = false;
    let expectedTotalAmount = null;

    document.addEventListener('DOMContentLoaded', async () => {
        if (!token) return redirectToLogin();
        document.getElementById('checkout-form').addEventListener('submit', openPaymentDialog);
        document.getElementById('checkout-confirm-form').addEventListener('submit', submitOrder);
        document.getElementById('checkout-confirm-back').addEventListener('click', () =>
            document.getElementById('checkout-confirm-dialog').close());
        try {
            const userResponse = await fetch('/api/auth/me', { headers: authHeaders() });
            if (userResponse.status === 401) return redirectToLogin();
            if (!userResponse.ok) throw new Error('Không thể xác minh tài khoản.');
            const user = await userResponse.json();
            if (user.role !== 'CUSTOMER') throw new Error('Chỉ tài khoản khách hàng được đặt mua.');
            document.getElementById('recipient-name').value = user.fullName || '';
            document.getElementById('recipient-phone').value = user.phone || '';
            await loadCart();
        } catch (error) { showNotice(error.message, true); }
    });

    async function loadCart() {
        const response = await fetch('/api/cart', { headers: authHeaders() });
        if (!response.ok) throw new Error(await responseMessage(response, 'Không thể tải giỏ hàng.'));
        const cart = await response.json();
        items = cart.items || [];
        const savedSelectedIds = sessionStorage.getItem('pawconnect.selectedCartItemIds');
        if (savedSelectedIds) {
            try {
                const selectedList = JSON.parse(savedSelectedIds);
                if (Array.isArray(selectedList) && selectedList.length > 0) {
                    const filtered = items.filter(it => selectedList.includes(it.id));
                    if (filtered.length > 0) items = filtered;
                }
            } catch (_) {}
        }
        const container = document.getElementById('checkout-items');
        container.replaceChildren();
        document.getElementById('confirm-order').disabled = true;
        if (!items.length) {
            selectedCouponId = null;
            quoteReady = false;
            document.getElementById('checkout-coupons').replaceChildren();
            document.getElementById('checkout-subtotal').textContent = formatMoney(0);
            document.getElementById('checkout-discount-row').hidden = true;
            document.getElementById('checkout-original').hidden = true;
            document.getElementById('coupon-loading').hidden = true;
            container.textContent = 'Giỏ hàng đang trống. Hãy chọn sản phẩm trước khi đặt mua.';
            document.getElementById('checkout-total').textContent = formatMoney(0);
            return;
        }
        const branches = new Set(items.map(item => item.branchId));
        if (branches.size !== 1 || branches.has(null)) {
            showNotice('Mỗi đơn chỉ được gồm sản phẩm từ một chi nhánh. Hãy bỏ sản phẩm khác chi nhánh.', true);
            branchId = null;
        } else {
            branchId = items[0].branchId;
            document.getElementById('checkout-branch').value = items[0].branchName || `Chi nhánh #${branchId}`;
            document.getElementById('checkout-notice').hidden = true;
        }
        let total = 0;
        items.forEach(item => {
            total += Number(item.price) * item.quantity;
            const row = document.createElement('div');
            row.className = 'commerce-item';
            row.innerHTML = '<div><strong class="commerce-item-name"></strong><small class="commerce-item-meta"></small></div><div class="commerce-item-right"><strong class="commerce-item-total"></strong><button class="commerce-remove" type="button">Bỏ</button></div>';
            row.querySelector('.commerce-item-name').textContent = item.productName;
            row.querySelector('.commerce-item-meta').textContent = `${item.branchName} · ${item.quantity} × ${formatMoney(item.price)}`;
            row.querySelector('.commerce-item-total').textContent = formatMoney(Number(item.price) * item.quantity);
            row.querySelector('.commerce-remove').addEventListener('click', () => removeItem(item.id));
            container.append(row);
        });
        document.getElementById('checkout-total').textContent = formatMoney(total);
        const signature = items.map(item => `${item.id}:${item.quantity}`).sort().join('|');
        if (sessionStorage.getItem('pawconnect.checkoutSignature') !== signature) {
            sessionStorage.setItem('pawconnect.checkoutSignature', signature);
            sessionStorage.setItem('pawconnect.checkoutKey', newConfirmationKey());
        }
        confirmationKey = sessionStorage.getItem('pawconnect.checkoutKey');
        if (branchId) await refreshQuote(!hasSuggested);
    }

    async function refreshQuote(suggest = false) {
        const sequence = ++quoteSequence;
        quoteReady = false;
        document.getElementById('confirm-order').disabled = true;
        document.getElementById('coupon-loading').hidden = false;
        document.querySelectorAll('#checkout-coupons input').forEach(input => input.disabled = true);
        const request = async id => {
            const response = await fetch('/api/coupons/quote', {
                method: 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
                body: JSON.stringify({ branchId, itemIds: items.map(item => item.id), couponId: id })
            });
            if (!response.ok) throw new Error(await responseMessage(response, 'Không thể kiểm tra mã giảm giá. Vui lòng tải lại trang.'));
            return response.json();
        };
        try {
            let quote;
            try { quote = await request(selectedCouponId); }
            catch (error) {
                if (sequence !== quoteSequence || selectedCouponId === null) throw error;
                selectedCouponId = null;
                showNotice(error.message, true);
                quote = await request(null);
            }
            if (sequence !== quoteSequence) return;
            if (suggest && quote.recommendedCouponId != null) {
                selectedCouponId = quote.recommendedCouponId;
                quote = await request(selectedCouponId);
                if (sequence !== quoteSequence) return;
            }
            hasSuggested = true;
            selectedCouponId = quote.selectedCouponId;
            renderCoupons(quote);
            document.getElementById('checkout-subtotal').textContent = formatMoney(quote.subtotal);
            document.getElementById('checkout-total').textContent = formatMoney(quote.totalAmount);
            expectedTotalAmount = quote.totalAmount;
            const discounted = Number(quote.discountAmount) > 0;
            document.getElementById('checkout-discount-row').hidden = !discounted;
            document.getElementById('checkout-discount').textContent = `−${formatMoney(quote.discountAmount)}`;
            const selected = quote.coupons.find(c => c.id === selectedCouponId);
            document.getElementById('checkout-coupon-label').textContent = selected ? `Giảm giá (${selected.code})` : 'Giảm giá';
            document.getElementById('checkout-original').hidden = !discounted;
            document.getElementById('checkout-original').textContent = formatMoney(quote.subtotal);
            quoteReady = true;
            document.getElementById('confirm-order').disabled = false;
        } catch (error) { if (sequence === quoteSequence) showNotice(error.message, true); }
        finally { if (sequence === quoteSequence) document.getElementById('coupon-loading').hidden = true; }
    }

    function renderCoupons(quote) {
        const list = document.getElementById('checkout-coupons');
        list.replaceChildren();
        quote.coupons.forEach(coupon => {
            const label = document.createElement('label');
            label.className = `coupon-choice${coupon.eligible ? '' : ' coupon-unavailable'}`;
            const input = document.createElement('input');
            input.type = 'checkbox'; input.className = 'form-check-input'; input.value = coupon.id;
            input.checked = coupon.id === selectedCouponId; input.disabled = !coupon.eligible;
            input.addEventListener('change', async () => {
                selectedCouponId = input.checked ? coupon.id : null;
                document.querySelectorAll('#checkout-coupons input').forEach(other => other.checked = String(selectedCouponId) === other.value);
                await refreshQuote();
            });
            const content = document.createElement('span');
            const title = document.createElement('strong'); title.textContent = `${coupon.code} · ${coupon.description}`;
            const name = document.createElement('span'); name.textContent = coupon.name;
            const remaining = document.createElement('small'); remaining.textContent = `Còn ${coupon.remaining} lượt của bạn${coupon.remaining === 0 ? ' · Hết mã' : ''}`;
            content.append(title, name, remaining);
            if (coupon.id === quote.recommendedCouponId && coupon.eligible) {
                const badge = document.createElement('small'); badge.className = 'coupon-recommended'; badge.textContent = 'Gợi ý: tiết kiệm nhiều nhất'; content.append(badge);
            }
            if (coupon.reason) { const reason = document.createElement('small'); reason.textContent = coupon.reason; content.append(reason); }
            label.append(input, content); list.append(label);
        });
        if (!quote.coupons.length) list.textContent = 'Hiện chưa có mã giảm giá.';
    }

    async function removeItem(id) {
        try {
            const response = await fetch(`/api/cart/items/${id}`, { method: 'DELETE', headers: authHeaders() });
            if (!response.ok) throw new Error(await responseMessage(response, 'Không thể bỏ sản phẩm.'));
            await loadCart();
            window.PawConnectCart?.refreshCount();
        } catch (error) { showNotice(error.message, true); }
    }

    function openPaymentDialog(event) {
        event.preventDefault();
        if (!branchId || !confirmationKey || !items.length || !quoteReady) return;
        document.getElementById('confirm-branch').textContent = document.getElementById('checkout-branch').value;
        document.getElementById('confirm-recipient').textContent = document.getElementById('recipient-name').value.trim();
        document.getElementById('confirm-amount').textContent = document.getElementById('checkout-total').textContent;
        document.getElementById('checkout-confirm-dialog').showModal();
    }

    async function submitOrder(event) {
        event.preventDefault();
        if (!branchId || !confirmationKey || !items.length || !quoteReady) return;
        const button = document.getElementById('confirm-order');
        const dialogButton = document.getElementById('checkout-confirm-submit');
        button.disabled = true;
        dialogButton.disabled = true;
        showNotice('Đang ghi nhận xác nhận thanh toán của bạn…');
        try {
            const response = await fetch('/api/orders', {
                method: 'POST',
                headers: { ...authHeaders(), 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    branchId,
                    idempotencyKey: confirmationKey,
                    recipientName: document.getElementById('recipient-name').value.trim(),
                    recipientPhone: document.getElementById('recipient-phone').value.trim(),
                    deliveryAddress: document.getElementById('delivery-address').value.trim(),
                    itemIds: items.map(it => it.id),
                    couponId: selectedCouponId,
                    expectedTotalAmount
                })
            });
            if (!response.ok) throw new Error(await responseMessage(response, 'Không thể xác nhận đơn.'));
            const order = await response.json();
            sessionStorage.removeItem('pawconnect.checkoutSignature');
            sessionStorage.removeItem('pawconnect.checkoutKey');
            sessionStorage.removeItem('pawconnect.selectedCartItemIds');
            window.location.assign(`/orders/${order.id}/receipt`);
        } catch (error) {
            document.getElementById('checkout-confirm-dialog').close();
            showNotice(error.message, true);
            button.disabled = false;
            dialogButton.disabled = false;
            await refreshQuote();
        }
    }

    function authHeaders() { return { Authorization: `Bearer ${token}` }; }
    function redirectToLogin() { window.location.assign('/auth/login?returnTo=%2Fcheckout'); }
    function newConfirmationKey() { return globalThis.crypto?.randomUUID?.() || `local-${Date.now()}-${Math.random().toString(36).slice(2)}`; }
    function formatMoney(value) { return `${new Intl.NumberFormat('vi-VN').format(value || 0)} VND`; }
    async function responseMessage(response, fallback) { try { const body = await response.json(); return body.message || Object.values(body.errors || {})[0] || fallback; } catch (_) { return fallback; } }
    function showNotice(message, error = false) { const notice = document.getElementById('checkout-notice'); notice.textContent = message; notice.classList.toggle('error', error); notice.hidden = false; }
})();
