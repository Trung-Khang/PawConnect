(() => {
    const tokenKey = 'pawconnect.accessToken';
    const statusLabels = { PAID: 'Đã thanh toán', CONFIRMED: 'Đã xác nhận', PREPARING: 'Đang chuẩn bị', SHIPPING: 'Đang giao', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã hủy', PENDING: 'Chờ xác nhận', SHIPPED: 'Đang giao', DELIVERED: 'Đã giao' };
    const nextStatus = { PAID: 'CONFIRMED', CONFIRMED: 'PREPARING', PREPARING: 'SHIPPING', SHIPPING: 'COMPLETED' };

    document.addEventListener('DOMContentLoaded', async () => {
        document.getElementById('reload-orders').addEventListener('click', loadOrders);
        try {
            const me = await fetch('/api/auth/me', { headers: authHeaders() });
            if (!me.ok) return showNotice('Vui lòng đăng nhập bằng tài khoản quản lý để xem đơn hàng.', true);
            const user = await me.json();
            if (!['ADMIN', 'BRANCH_MANAGER'].includes(user.role)) return showNotice('Bạn không có quyền quản lý đơn hàng.', true);
            await loadOrders();
        } catch (_) { showNotice('Không thể xác minh tài khoản.', true); }
    });

    function authHeaders() {
        const token = localStorage.getItem(tokenKey);
        return token ? { Authorization: `Bearer ${token}` } : {};
    }

    async function loadOrders() {
        try {
            const response = await fetch('/api/orders/manage', { headers: authHeaders() });
            if (!response.ok) throw new Error(await responseMessage(response, 'Không thể tải danh sách đơn hàng.'));
            const orders = await response.json();
            const body = document.getElementById('orders-table-body');
            body.replaceChildren();
            if (!orders.length) {
                body.innerHTML = '<tr><td colspan="7">Chưa có đơn hàng nào trong phạm vi quản lý.</td></tr>';
                return;
            }
            orders.forEach(order => body.append(renderOrder(order)));
        } catch (error) { showNotice(error.message, true); }
    }

    function renderOrder(order) {
        const row = document.createElement('tr');
        row.innerHTML = '<td></td><td></td><td></td><td class="order-items"></td><td></td><td></td><td class="order-actions"><span class="order-status-label"></span> <a class="order-receipt-link">Biên lai</a></td>';
        row.children[0].textContent = `#${order.id}`;
        row.children[1].textContent = formatDate(order.createdAt);
        row.children[2].textContent = order.branchName || `Chi nhánh #${order.branchId}`;
        row.children[3].textContent = (order.items || []).map(item => `${item.productName} × ${item.quantity}`).join(', ') || 'Không có sản phẩm';
        row.children[4].textContent = formatMoney(order.totalAmount);
        row.children[5].textContent = order.paymentMethod === 'SIMULATED'
            ? order.paymentStatus === 'REFUNDED' ? 'Đã hoàn tiền' : 'Đã thanh toán'
            : 'Chưa ghi nhận';
        row.querySelector('.order-status-label').textContent = statusLabels[order.status] || order.status;
        row.querySelector('.order-receipt-link').href = `/orders/${order.id}/receipt`;

        const next = nextStatus[order.status];
        if (next) {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'order-status-action';
            button.textContent = `Chuyển: ${statusLabels[next]}`;
            button.addEventListener('click', () => updateStatus(order.id, next));
            row.lastElementChild.append(button);
        }
        if (order.status === 'PAID') {
            const cancel = document.createElement('button');
            cancel.type = 'button';
            cancel.className = 'order-status-action order-cancel';
            cancel.textContent = 'Hủy đơn';
            cancel.addEventListener('click', () => {
                const reason = window.prompt(`Lý do hủy đơn #${order.id}:`);
                if (reason?.trim()) updateStatus(order.id, 'CANCELLED', reason.trim());
            });
            row.lastElementChild.append(cancel);
        }
        return row;
    }

    async function updateStatus(id, status, reason = '') {
        try {
            const query = new URLSearchParams({ status });
            if (reason) query.set('reason', reason);
            const response = await fetch(`/api/orders/${id}/status?${query}`, { method: 'PUT', headers: authHeaders() });
            if (!response.ok) throw new Error(await responseMessage(response, 'Không thể cập nhật trạng thái đơn hàng.'));
            showNotice('Đã cập nhật trạng thái đơn hàng.');
            await loadOrders();
        } catch (error) { showNotice(error.message, true); }
    }

    function showNotice(message, error = false) {
        const notice = document.getElementById('orders-notice');
        notice.textContent = message;
        notice.classList.toggle('error', error);
        notice.hidden = false;
    }
    async function responseMessage(response, fallback) { try { return (await response.json()).message || fallback; } catch (_) { return fallback; } }
    function formatMoney(value) { return `${new Intl.NumberFormat('vi-VN').format(value || 0)} VND`; }
    function formatDate(value) { return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—'; }
})();
