(() => {
    const token = localStorage.getItem('pawconnect.accessToken');
    const progress = window.PawConnectOrderProgress;
    const adoptionLabels = { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối' };
    let pendingCancellation = null;

    document.addEventListener('DOMContentLoaded', async () => {
        if (!token) return window.location.assign('/auth/login?returnTo=%2Forders');
        document.getElementById('purchase-tab').addEventListener('click', () => showTab('purchase'));
        document.getElementById('adoption-tab').addEventListener('click', () => showTab('adoption'));
        document.getElementById('cancel-order-back').addEventListener('click', () =>
            document.getElementById('cancel-order-dialog').close());
        document.getElementById('cancel-order-dialog').addEventListener('close', () => { pendingCancellation = null; });
        document.getElementById('cancel-order-form').addEventListener('submit', submitCancellation);
        document.getElementById('cancel-order-reason').addEventListener('input', event =>
            event.currentTarget.setCustomValidity(''));
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
            card.className = 'commerce-panel commerce-order-card';
            card.innerHTML = '<div class="row g-3 align-items-start"><div class="col-12 col-md-8"><h2 class="order-title"></h2><span class="order-status-badge"></span><p class="order-meta mt-2"></p><p class="order-items"></p><p class="order-payment"></p></div><div class="col-12 col-md-4 order-side"><strong class="order-total"></strong><a class="btn-pill" href="#">Xem biên lai</a><button type="button" class="commerce-remove" hidden>Hủy đơn và hoàn tiền</button></div></div>';
            card.querySelector('.order-title').textContent = `Đơn #${order.id}`;
            const badge = card.querySelector('.order-status-badge');
            badge.textContent = customerStatusLabel(order.status);
            if (order.status === 'COMPLETED') badge.classList.add('is-finished');
            if (order.status === 'CANCELLED') badge.classList.add('is-cancelled');
            card.querySelector('.order-meta').textContent = `${formatDate(order.createdAt)} · ${order.branchName || `Chi nhánh #${order.branchId}`}`;
            card.querySelector('.order-items').textContent = (order.items || []).map(item => `${item.productName} × ${item.quantity}`).join(', ');
            card.querySelector('.order-payment').textContent = `Thanh toán: ${paymentLabel(order)}`;
            card.querySelector('.order-total').textContent = formatMoney(order.totalAmount);
            const detailsLink = card.querySelector('a');
            detailsLink.href = `/orders/${order.id}/receipt`;
            detailsLink.textContent = ['PAID', 'REFUNDED'].includes(order.paymentStatus) ? 'Xem biên lai' : 'Xem đơn hàng';
            card.append(progress.render(order, { customerView: true }));
            const cancel = card.querySelector('button');
            const processing = ['PROCESSING', 'PAID', 'CONFIRMED', 'PREPARING'].includes(order.status);
            const pending = (order.cancellationRequests || []).some(request => request.status === 'PENDING');
            cancel.hidden = !(order.status === 'PAYMENT_REPORTED' || (processing && !pending));
            cancel.textContent = processing ? 'Gửi yêu cầu hủy' : 'Hủy đơn';
            cancel.addEventListener('click', () => cancelOrder(order, processing));
            if (['SHIPPING', 'COMPLETED'].includes(order.status)) {
                const support = document.createElement('button');
                support.type = 'button';
                support.className = 'btn btn-outline-secondary';
                support.textContent = 'Liên hệ hỗ trợ';
                support.addEventListener('click', () => {
                    const chat = document.getElementById('chatWidgetToggle');
                    if (chat) chat.click();
                    else showNotice(order.branchPhone
                        ? `Liên hệ chi nhánh: ${order.branchPhone}`
                        : 'Vui lòng liên hệ chi nhánh để được hỗ trợ về đơn hàng.', true);
                });
                card.querySelector('.order-side').append(support);
            }
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

    function cancelOrder(order, requestOnly) {
        pendingCancellation = { order, requestOnly };
        document.getElementById('cancel-order-title').textContent = requestOnly
            ? `Gửi yêu cầu hủy đơn #${order.id}?` : `Hủy đơn #${order.id}?`;
        document.getElementById('cancel-order-description').textContent = requestOnly
            ? 'Chi nhánh sẽ xem xét và trả lời. Đơn chưa bị hủy hoặc hoàn tiền khi yêu cầu còn chờ; chi nhánh không thể bàn giao vận chuyển trước khi trả lời.'
            : 'Đơn sẽ được hủy và tồn kho được khôi phục. Chi nhánh chưa ghi nhận thanh toán nên không ghi hoàn tiền.';
        document.getElementById('cancel-order-submit').textContent = requestOnly ? 'Gửi yêu cầu hủy' : 'Xác nhận hủy đơn';
        const input = document.getElementById('cancel-order-reason');
        input.value = '';
        input.setCustomValidity('');
        document.getElementById('cancel-order-dialog').showModal();
    }

    async function submitCancellation(event) {
        event.preventDefault();
        if (!pendingCancellation) return;
        const { order, requestOnly } = pendingCancellation;
        const input = document.getElementById('cancel-order-reason');
        const reason = input.value.trim();
        if (!reason) {
            input.setCustomValidity('Vui lòng nhập lý do hủy đơn.');
            input.reportValidity();
            return;
        }
        const submit = document.getElementById('cancel-order-submit');
        submit.disabled = true;
        try {
            const query = new URLSearchParams({ reason });
            const result = await request(`/api/orders/${order.id}/${requestOnly ? 'cancellation-request' : 'cancel'}?${query}`, { method: 'POST' });
            document.getElementById('cancel-order-dialog').close();
            showNotice(requestOnly ? `Đã gửi yêu cầu hủy đơn #${order.id}; chờ chi nhánh trả lời.`
                : result.paymentStatus === 'REFUNDED' ? `Đã hủy đơn #${order.id} và hoàn tiền.` : `Đã hủy đơn #${order.id}.`);
            await loadPurchases();
        } catch (error) {
            document.getElementById('cancel-order-dialog').close();
            showNotice(error.message, true);
            await loadPurchases().catch(() => {});
        } finally { submit.disabled = false; }
    }

    async function request(path, options = {}) {
        const response = await fetch(path, { ...options, headers: { Authorization: `Bearer ${token}`, ...(options.headers || {}) } });
        if (!response.ok) {
            const body = await response.json().catch(() => ({}));
            throw new Error(body.message || 'Không thể tải dữ liệu đơn hàng.');
        }
        return response.json();
    }
    function paymentLabel(order) {
        if (order.paymentStatus === 'REFUNDED') return 'Đã hoàn tiền';
        if (order.paymentStatus === 'PAID') return 'Chi nhánh đã ghi nhận thanh toán';
        if (order.paymentStatus === 'CUSTOMER_CONFIRMED') return 'Bạn đã xác nhận · chờ chi nhánh xác nhận đã nhận';
        if (order.paymentStatus === 'CANCELLED') return 'Đã hủy trước khi ghi nhận thanh toán';
        return 'Chưa ghi nhận';
    }
    function customerStatusLabel(status) {
        return progress.labels[status] || status;
    }
    function formatMoney(value) { return `${new Intl.NumberFormat('vi-VN').format(value || 0)} VND`; }
    function formatDate(value) { return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—'; }
    function showNotice(message, error = false) { const notice = document.getElementById('orders-notice'); notice.textContent = message; notice.classList.toggle('error', error); notice.hidden = false; }
    function escapeHtml(value) { if (!value) return ''; const div = document.createElement('div'); div.textContent = value; return div.innerHTML; }
})();
