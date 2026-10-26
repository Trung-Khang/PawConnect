(() => {
    const token = localStorage.getItem('pawconnect.accessToken');
    const statusLabels = { PAID: 'Đã thanh toán', CONFIRMED: 'Đã xác nhận', PREPARING: 'Đang chuẩn bị', SHIPPING: 'Đang giao', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã hủy', PENDING: 'Chờ xác nhận', SHIPPED: 'Đang giao', DELIVERED: 'Đã giao' };
    const adoptionLabels = { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối' };

    document.addEventListener('DOMContentLoaded', async () => {
        if (!token) return window.location.assign('/auth/login?returnTo=%2Forders');
        document.getElementById('purchase-tab').addEventListener('click', () => showTab('purchase'));
        document.getElementById('adoption-tab').addEventListener('click', () => showTab('adoption'));
        try {
            const me = await request('/api/auth/me');
            if (me.role !== 'CUSTOMER') throw new Error('Trang này chỉ dành cho tài khoản khách hàng.');
            await loadPurchases();
        } catch (error) { showNotice(error.message, true); }
    });

    function showTab(kind) {
        const adoption = kind === 'adoption';
        document.getElementById('purchase-orders').hidden = adoption;
        document.getElementById('adoption-orders').hidden = !adoption;
        for (const [id, selected] of [['purchase-tab', !adoption], ['adoption-tab', adoption]]) {
            const tab = document.getElementById(id);
            tab.classList.toggle('active', selected);
            tab.setAttribute('aria-selected', String(selected));
        }
        if (adoption) loadAdoptions();
    }

    async function loadPurchases() {
        const orders = await request('/api/orders/my');
        const list = document.getElementById('purchase-orders');
        list.replaceChildren();
        if (!orders.length) { list.textContent = 'Bạn chưa có đơn mua hàng nào.'; return; }
        orders.forEach(order => {
            const card = document.createElement('article');
            card.className = 'commerce-panel commerce-order';
            card.innerHTML = '<div><h2 class="order-title"></h2><p class="order-meta"></p><p class="order-items"></p><p class="order-payment"></p></div><div class="order-side"><strong class="order-total"></strong><a class="btn-pill" href="#">Xem biên lai</a><button type="button" class="commerce-remove" hidden>Hủy đơn</button></div>';
            card.querySelector('.order-title').textContent = `Đơn #${order.id} · ${statusLabels[order.status] || order.status}`;
            card.querySelector('.order-meta').textContent = `${formatDate(order.createdAt)} · ${order.branchName || `Chi nhánh #${order.branchId}`}`;
            card.querySelector('.order-items').textContent = (order.items || []).map(item => `${item.productName} × ${item.quantity}`).join(', ');
            card.querySelector('.order-payment').textContent = `Thanh toán: ${paymentLabel(order)}`;
            card.querySelector('.order-total').textContent = formatMoney(order.totalAmount);
            card.querySelector('a').href = `/orders/${order.id}/receipt`;
            const cancel = card.querySelector('button');
            cancel.hidden = order.status !== 'PAID';
            cancel.addEventListener('click', () => cancelOrder(order.id));
            list.append(card);
        });
    }

    async function loadAdoptions() {
        const list = document.getElementById('adoption-orders');
        list.textContent = 'Đang tải đơn nhận nuôi…';
        try {
            const applications = await request('/api/adoptions/applications/my');
            list.replaceChildren();
            if (!applications.length) { list.textContent = 'Bạn chưa có đơn nhận nuôi nào.'; return; }
            applications.forEach(application => {
                const card = document.createElement('article');
                card.className = 'commerce-panel commerce-order';

                let statusNoticeHtml = '';
                if (application.status === 'APPROVED') {
                    statusNoticeHtml = `
                        <div style="margin: 0.6rem 0; padding: 0.65rem 0.9rem; background: #e8f5e9; color: #2e7d32; border-radius: 8px; border-left: 4px solid #4caf50; font-size: 0.92rem; font-weight: 500;">
                            <i class="fas fa-check-circle"></i> <strong>Đơn nhận nuôi đã được duyệt!</strong> Nhân viên chi nhánh sẽ chủ động liên hệ với bạn trong vài giờ sắp tới để sắp xếp thời gian rước bé.
                        </div>`;
                } else if (application.status === 'REJECTED') {
                    statusNoticeHtml = `
                        <div style="margin: 0.6rem 0; padding: 0.65rem 0.9rem; background: #ffebee; color: #c62828; border-radius: 8px; border-left: 4px solid #ef5350; font-size: 0.92rem;">
                            <i class="fas fa-circle-xmark"></i> <strong>Lý do từ chối từ nhân viên:</strong> ${escapeHtml(application.rejectionReason || 'Hồ sơ chưa phù hợp hoặc bài nhận nuôi đã đóng.')}
                        </div>`;
                } else {
                    statusNoticeHtml = `
                        <div style="margin: 0.6rem 0; padding: 0.65rem 0.9rem; background: #fffde7; color: #f57f17; border-radius: 8px; border-left: 4px solid #fbc02d; font-size: 0.92rem;">
                            <i class="fas fa-clock"></i> Đơn đang chờ nhân viên chi nhánh xét duyệt hồ sơ.
                        </div>`;
                }

                card.innerHTML = `
                    <div style="flex: 1;">
                        <h2 class="order-title">Đơn nhận nuôi #${application.id} · ${adoptionLabels[application.status] || application.status}</h2>
                        <p class="order-meta">Gửi lúc ${formatDate(application.createdAt)}</p>
                        <div style="margin: 0.5rem 0; padding: 0.65rem 0.9rem; background: #fafafa; border-radius: 8px; border: 1px solid #eee; font-size: 0.9rem;">
                            <strong>Nội dung đăng ký đã gửi:</strong><br>
                            <span style="color: var(--text-main);">${escapeHtml(application.message)}</span>
                        </div>
                        ${statusNoticeHtml}
                    </div>
                    <div class="order-side" style="align-self: flex-start; margin-top: 0.5rem;">
                        <a class="btn-pill" href="/adoptions/${application.adoptionPostId}">Xem bài nhận nuôi</a>
                    </div>
                `;
                list.append(card);
            });
        } catch (error) { list.textContent = error.message; }
    }

    async function cancelOrder(id) {
        if (!window.confirm(`Hủy đơn #${id} trước khi chi nhánh xác nhận?`)) return;
        try {
            await request(`/api/orders/${id}/cancel`, { method: 'POST' });
            showNotice(`Đã hủy đơn #${id} và hoàn tiền.`);
            await loadPurchases();
        } catch (error) { showNotice(error.message, true); }
    }

    async function request(path, options = {}) {
        const response = await fetch(path, { ...options, headers: { Authorization: `Bearer ${token}`, ...(options.headers || {}) } });
        if (!response.ok) {
            const body = await response.json().catch(() => ({}));
            throw new Error(body.message || 'Không thể tải dữ liệu đơn hàng.');
        }
        return response.json();
    }
    function paymentLabel(order) { return order.paymentMethod === 'SIMULATED' ? (order.paymentStatus === 'REFUNDED' ? 'Đã hoàn tiền' : 'Đã thanh toán') : 'Chưa ghi nhận'; }
    function formatMoney(value) { return `${new Intl.NumberFormat('vi-VN').format(value || 0)} VND`; }
    function formatDate(value) { return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—'; }
    function showNotice(message, error = false) { const notice = document.getElementById('orders-notice'); notice.textContent = message; notice.classList.toggle('error', error); notice.hidden = false; }
    function escapeHtml(text) {
        if (!text) return '';
        const d = document.createElement('div');
        d.textContent = text;
        return d.innerHTML;
    }
})();
