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

                let statusBadgeClass = 'text-warning';
                if (application.status === 'APPROVED') statusBadgeClass = 'text-success';
                else if (application.status === 'REJECTED') statusBadgeClass = 'text-danger';

                let statusBannerHtml = '';
                if (application.status === 'APPROVED') {
                    statusBannerHtml = `
                        <div style="background: #ecfdf5; border: 1.5px solid #a7f3d0; border-radius: 10px; padding: 0.75rem 1rem; margin-top: 0.75rem; color: #065f46; font-size: 0.92rem;">
                            <strong><i class="fas fa-check-circle"></i> Đơn đã được duyệt!</strong>
                            <p style="margin: 4px 0 0; font-size: 0.88rem;">Vui lòng kiểm tra inbox (hộp thư) để trao đổi thêm với nhân viên về thời gian và thủ tục qua nhận bé nhé.</p>
                        </div>
                    `;
                } else if (application.status === 'REJECTED') {
                    statusBannerHtml = `
                        <div style="background: #fff1f2; border: 1.5px solid #fecdd3; border-radius: 10px; padding: 0.75rem 1rem; margin-top: 0.75rem; color: #9f1239; font-size: 0.92rem;">
                            <strong><i class="fas fa-times-circle"></i> Đơn đã bị từ chối</strong>
                            <p style="margin: 4px 0 0; font-size: 0.88rem;"><strong>Lý do từ chối:</strong> ${escapeHtml(application.rejectionReason || 'Chưa đáp ứng điều kiện nhận nuôi của chi nhánh.')}</p>
                        </div>
                    `;
                } else {
                    statusBannerHtml = `
                        <div style="background: #fffbeb; border: 1.5px solid #fde68a; border-radius: 10px; padding: 0.75rem 1rem; margin-top: 0.75rem; color: #92400e; font-size: 0.92rem;">
                            <i class="fas fa-hourglass-half"></i> Đơn đang chờ nhân viên chi nhánh xem xét và phản hồi.
                        </div>
                    `;
                }

                card.innerHTML = `
                    <div style="flex: 1;">
                        <h2 class="order-title">🐾 Đơn nhận nuôi: ${escapeHtml(application.dogName || `#${application.id}`)} · <span class="${statusBadgeClass}">${adoptionLabels[application.status] || application.status}</span></h2>
                        <p class="order-meta">${application.branchName ? `Chi nhánh: ${escapeHtml(application.branchName)} · ` : ''}Gửi lúc ${formatDate(application.createdAt)}</p>
                        ${application.message ? `<p class="order-items" style="font-style: italic; color: var(--text-muted); margin: 0.35rem 0;">"${escapeHtml(application.message)}"</p>` : ''}
                        ${statusBannerHtml}
                    </div>
                    <div class="order-side" style="align-self: flex-start;">
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
    function escapeHtml(value) { if (!value) return ''; const div = document.createElement('div'); div.textContent = value; return div.innerHTML; }
})();
