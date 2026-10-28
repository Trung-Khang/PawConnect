(() => {
    const progress = window.PawConnectOrderProgress;
    const nextStatus = { PROCESSING: 'SHIPPING', PAID: 'SHIPPING', CONFIRMED: 'SHIPPING', PREPARING: 'SHIPPING', SHIPPING: 'COMPLETED' };
    const actionLabels = {
        PROCESSING: 'Xác nhận thanh toán và tiếp nhận đơn',
        SHIPPING: 'Xác nhận bàn giao vận chuyển',
        COMPLETED: 'Xác nhận giao thành công',
        APPROVE_CANCELLATION: 'Chấp nhận yêu cầu hủy',
        REJECT_CANCELLATION: 'Từ chối yêu cầu hủy'
    };
    const actionDescriptions = {
        PROCESSING: 'Chỉ xác nhận khi đã kiểm tra khoản thanh toán và có thể nhận xử lý đơn. Sau bước này khách có thể gửi yêu cầu hủy cho tới khi bàn giao vận chuyển.',
        SHIPPING: 'Chỉ xác nhận khi hàng đã thực sự bàn giao cho người vận chuyển hoặc bắt đầu giao bởi chi nhánh.',
        COMPLETED: 'Chỉ xác nhận sau khi người nhận đã nhận hàng thành công.'
    };
    let pendingAction = null;

    document.addEventListener('DOMContentLoaded', async () => {
        document.getElementById('reload-orders').addEventListener('click', loadOrders);
        document.getElementById('order-action-back').addEventListener('click', closeDialog);
        document.getElementById('order-action-dialog').addEventListener('close', () => { pendingAction = null; });
        document.getElementById('order-action-form').addEventListener('submit', submitAction);
        document.getElementById('order-action-reason').addEventListener('input', event =>
            event.currentTarget.setCustomValidity(''));
        try {
            const me = await fetch('/api/auth/me', { headers: authHeaders() });
            if (!me.ok) return showNotice('Vui lòng đăng nhập bằng tài khoản quản lý để xem đơn hàng.', true);
            const user = await me.json();
            if (!['ADMIN', 'BRANCH_MANAGER'].includes(user.role)) return showNotice('Bạn không có quyền quản lý đơn hàng.', true);
            await loadOrders();
        } catch (_) { showNotice('Không thể xác minh tài khoản.', true); }
    });

    function authHeaders() {
        const token = localStorage.getItem('pawconnect.accessToken');
        return token ? { Authorization: `Bearer ${token}` } : {};
    }

    async function loadOrders() {
        const list = document.getElementById('managed-orders');
        try {
            const response = await fetch('/api/orders/manage', { headers: authHeaders() });
            if (!response.ok) throw new Error(await responseMessage(response, 'Không thể tải danh sách đơn hàng.'));
            const orders = await response.json();
            list.replaceChildren();
            if (!orders.length) { list.textContent = 'Chưa có đơn hàng nào trong phạm vi quản lý.'; return; }
            orders.forEach(order => list.append(renderOrder(order)));
        } catch (error) { list.textContent = ''; showNotice(error.message, true); }
    }

    function renderOrder(order) {
        const card = document.createElement('article');
        card.className = 'orders-card card border-0 shadow-sm';
        const body = document.createElement('div');
        body.className = 'card-body p-3 p-md-4';
        const heading = document.createElement('div');
        heading.className = 'd-flex flex-column flex-sm-row justify-content-between align-items-start gap-2 mb-3';
        const titleGroup = document.createElement('div');
        const title = document.createElement('h2');
        title.className = 'h5 mb-1';
        title.textContent = `Đơn #${order.id}`;
        const date = document.createElement('p');
        date.className = 'text-muted small mb-0';
        date.textContent = progress.formatDate(order.createdAt);
        titleGroup.append(title, date);
        heading.append(titleGroup, statusBadge(order.status));
        body.append(heading);

        const details = document.createElement('div');
        details.className = 'row g-3 orders-card-details';
        details.append(detail('Chi nhánh', order.branchName || `Chi nhánh #${order.branchId}`));
        details.append(detail('Sản phẩm', (order.items || []).map(item => `${item.productName} × ${item.quantity}`).join(', ') || 'Không có sản phẩm'));
        details.append(detail('Người nhận', [order.recipientName, order.recipientPhone].filter(Boolean).join(' · ') || 'Chưa ghi nhận'));
        details.append(detail('Địa chỉ giao', order.deliveryAddress || 'Chưa ghi nhận'));
        details.append(detail('Tổng tiền', formatMoney(order.totalAmount)));
        details.append(detail('Thanh toán', paymentLabel(order)));
        body.append(details, progress.render(order));

        const actions = document.createElement('div');
        actions.className = 'orders-card-actions d-flex flex-wrap gap-2 mt-3';
        const receipt = document.createElement('a');
        receipt.className = 'btn btn-outline-secondary';
        receipt.href = `/orders/${order.id}/receipt`;
        receipt.textContent = order.paymentStatus === 'PAID' || order.paymentStatus === 'REFUNDED'
            ? 'Xem biên lai' : 'Xem đơn hàng';
        actions.append(receipt);
        const pendingRequest = (order.cancellationRequests || []).find(request => request.status === 'PENDING');
        if (order.status === 'PAYMENT_REPORTED') {
            const confirmPayment = document.createElement('button');
            confirmPayment.type = 'button';
            confirmPayment.className = 'btn btn-primary';
            confirmPayment.textContent = actionLabels.PROCESSING;
            confirmPayment.addEventListener('click', () => openDialog(order, 'PROCESSING'));
            actions.append(confirmPayment);
        }
        const next = nextStatus[order.status];
        if (next && !pendingRequest) {
            const advance = document.createElement('button');
            advance.type = 'button';
            advance.className = 'btn btn-primary';
            advance.textContent = actionLabels[next];
            advance.addEventListener('click', () => openDialog(order, next));
            actions.append(advance);
        }
        if (pendingRequest) {
            for (const action of ['APPROVE_CANCELLATION', 'REJECT_CANCELLATION']) {
                const decision = document.createElement('button');
                decision.type = 'button';
                decision.className = action === 'APPROVE_CANCELLATION' ? 'btn btn-danger' : 'btn btn-outline-secondary';
                decision.textContent = actionLabels[action];
                decision.addEventListener('click', () => openDialog(order, action));
                actions.append(decision);
            }
        }
        if (!pendingRequest && ['PAYMENT_REPORTED', 'PROCESSING', 'PAID', 'CONFIRMED', 'PREPARING'].includes(order.status)) {
            const cancel = document.createElement('button');
            cancel.type = 'button';
            cancel.className = 'btn btn-outline-danger';
            cancel.textContent = order.paymentStatus === 'PAID' ? 'Hủy và hoàn tiền' : 'Hủy đơn';
            cancel.addEventListener('click', () => openDialog(order, 'CANCELLED'));
            actions.append(cancel);
        }
        body.append(actions);
        card.append(body);
        return card;
    }

    function detail(label, value) {
        const column = document.createElement('div');
        column.className = 'col-12 col-md-6 col-xl-4';
        const name = document.createElement('span');
        name.className = 'orders-detail-label';
        name.textContent = label;
        const content = document.createElement('span');
        content.className = 'orders-detail-value';
        content.textContent = value;
        column.append(name, content);
        return column;
    }

    function statusBadge(status) {
        const badge = document.createElement('span');
        badge.className = 'order-status-badge';
        if (status === 'COMPLETED') badge.classList.add('is-finished');
        if (status === 'CANCELLED') badge.classList.add('is-cancelled');
        badge.textContent = progress.labels[status] || status;
        return badge;
    }

    function openDialog(order, status) {
        pendingAction = { id: order.id, status, paymentStatus: order.paymentStatus };
        const cancelling = status === 'CANCELLED';
        const approving = status === 'APPROVE_CANCELLATION';
        const rejecting = status === 'REJECT_CANCELLATION';
        const refund = order.paymentStatus === 'PAID';
        document.getElementById('order-action-title').textContent = cancelling
            ? `Hủy đơn #${order.id}${refund ? ' và hoàn tiền' : ''}?` : `${actionLabels[status]} #${order.id}?`;
        document.getElementById('order-action-description').textContent = cancelling
            ? refund
                ? 'Đơn sẽ chuyển sang Đã hủy, thanh toán chuyển sang Đã hoàn tiền và tồn kho được khôi phục. Không thể hoàn tác thao tác này.'
                : 'Đơn sẽ bị hủy và tồn kho được khôi phục. Chi nhánh chưa xác nhận đã nhận thanh toán nên không ghi nhận hoàn tiền. Không thể hoàn tác thao tác này.'
            : approving ? 'Đơn sẽ hủy, ghi hoàn tiền mô phỏng và khôi phục tồn kho. Phải ghi lý do chấp nhận yêu cầu.'
            : rejecting ? 'Đơn tiếp tục được xử lý. Khách sẽ thấy lý do từ chối trong lịch sử yêu cầu hủy.'
            : `${progress.labels[order.status] || order.status} → ${progress.labels[status]}. ${actionDescriptions[status]}`;
        const reason = document.getElementById('order-action-reason');
        reason.value = '';
        reason.setCustomValidity('');
        reason.required = cancelling || approving || rejecting;
        document.getElementById('order-reason-required').hidden = !reason.required;
        const submit = document.getElementById('order-action-submit');
        submit.textContent = cancelling ? (refund ? 'Xác nhận hủy và hoàn tiền' : 'Xác nhận hủy đơn') : actionLabels[status];
        submit.classList.toggle('btn-danger', cancelling || approving);
        submit.classList.toggle('btn-primary', !cancelling && !approving);
        document.getElementById('order-action-dialog').showModal();
    }

    function closeDialog() { document.getElementById('order-action-dialog').close(); }

    async function submitAction(event) {
        event.preventDefault();
        if (!pendingAction) return;
        const { id, status, paymentStatus } = pendingAction;
        const input = document.getElementById('order-action-reason');
        const reason = input.value.trim();
        if (['CANCELLED', 'APPROVE_CANCELLATION', 'REJECT_CANCELLATION'].includes(status) && !reason) {
            input.setCustomValidity('Vui lòng nhập lý do quyết định.');
            input.reportValidity();
            return;
        }
        const button = document.getElementById('order-action-submit');
        button.disabled = true;
        try {
            let response;
            if (status === 'PROCESSING') {
                response = await fetch(`/api/orders/${id}/confirm-payment`, { method: 'POST', headers: authHeaders() });
            } else if (['APPROVE_CANCELLATION', 'REJECT_CANCELLATION'].includes(status)) {
                const decision = status === 'APPROVE_CANCELLATION' ? 'approve' : 'reject';
                response = await fetch(`/api/orders/${id}/cancellation-request/${decision}?${new URLSearchParams({ reason })}`,
                    { method: 'POST', headers: authHeaders() });
            } else {
                const query = new URLSearchParams({ status });
                if (reason) query.set('reason', reason);
                response = await fetch(`/api/orders/${id}/status?${query}`, { method: 'PUT', headers: authHeaders() });
            }
            if (!response.ok) throw new Error(await responseMessage(response, 'Không thể cập nhật trạng thái đơn hàng.'));
            closeDialog();
            showNotice(status === 'CANCELLED'
                ? `Đơn #${id} đã hủy${paymentStatus === 'PAID' ? ' và hoàn tiền' : ''}.`
                : status === 'APPROVE_CANCELLATION' ? `Đã chấp nhận hủy và hoàn tiền đơn #${id}.`
                : status === 'REJECT_CANCELLATION' ? `Đã từ chối yêu cầu hủy đơn #${id}.`
                : `Đơn #${id}: ${progress.labels[status]}.`);
            await loadOrders();
        } catch (error) { showNotice(error.message, true); await loadOrders(); }
        finally { button.disabled = false; }
    }

    function showNotice(message, error = false) {
        const notice = document.getElementById('orders-notice');
        notice.textContent = message;
        notice.classList.toggle('error', error);
        notice.hidden = false;
    }
    async function responseMessage(response, fallback) { try { return (await response.json()).message || fallback; } catch (_) { return fallback; } }
    function paymentLabel(order) {
        if (order.paymentStatus === 'REFUNDED') return 'Đã hoàn tiền';
        if (order.paymentStatus === 'PAID') return 'Đã nhận thanh toán';
        if (order.paymentStatus === 'CUSTOMER_CONFIRMED') return 'Khách xác nhận · chờ chi nhánh xác nhận đã nhận';
        if (order.paymentStatus === 'CANCELLED') return 'Đã hủy trước khi ghi nhận thanh toán';
        return 'Chưa ghi nhận';
    }
    function formatMoney(value) { return `${new Intl.NumberFormat('vi-VN').format(value || 0)} VND`; }
})();
