(() => {
    const tokenKey = 'pawconnect.accessToken';
    const statusLabels = { PENDING: 'Chờ xác nhận', CONFIRMED: 'Đã xác nhận', SHIPPED: 'Đang giao', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy' };

    document.addEventListener('DOMContentLoaded', async () => {
        document.getElementById('reload-orders').addEventListener('click', loadOrders);
        const me = await fetch('/api/auth/me', { headers: authHeaders() });
        if (!me.ok) return showNotice('Vui lòng đăng nhập bằng tài khoản quản lý để xem đơn hàng.', true);
        const user = await me.json();
        if (!['ADMIN', 'BRANCH_MANAGER'].includes(user.role)) return showNotice('Bạn không có quyền quản lý đơn hàng.', true);
        loadOrders();
    });

    function authHeaders() {
        const token = localStorage.getItem(tokenKey);
        return token ? { Authorization: `Bearer ${token}` } : {};
    }

    async function loadOrders() {
        const response = await fetch('/api/orders/manage', { headers: authHeaders() });
        if (!response.ok) return showNotice(await responseMessage(response, 'Không thể tải danh sách đơn hàng.'), true);
        const orders = await response.json();
        const body = document.getElementById('orders-table-body');
        body.replaceChildren();
        if (!orders.length) {
            body.innerHTML = '<tr><td colspan="6">Chưa có đơn hàng nào trong phạm vi quản lý.</td></tr>';
            return;
        }
        orders.forEach((order) => body.append(renderOrder(order)));
    }

    function renderOrder(order) {
        const row = document.createElement('tr');
        const select = document.createElement('select');
        select.className = 'order-status';
        Object.entries(statusLabels).forEach(([value, label]) => select.add(new Option(label, value, false, order.status === value)));
        select.addEventListener('change', () => updateStatus(order.id, select));
        const items = (order.items || []).map((item) => `${item.productName} x${item.quantity}`).join(', ') || 'Không có sản phẩm';
        row.innerHTML = `<td>#${order.id}</td><td>${formatDate(order.createdAt)}</td><td>Chi nhánh #${order.branchId}</td><td class="order-items"></td><td>${formatMoney(order.totalAmount)}</td><td></td>`;
        row.querySelector('.order-items').textContent = items;
        row.lastElementChild.append(select);
        return row;
    }

    async function updateStatus(id, select) {
        select.disabled = true;
        const response = await fetch(`/api/orders/${id}/status?status=${encodeURIComponent(select.value)}`, { method: 'PUT', headers: authHeaders() });
        select.disabled = false;
        if (!response.ok) {
            showNotice(await responseMessage(response, 'Không thể cập nhật trạng thái đơn hàng.'), true);
            loadOrders();
            return;
        }
        showNotice('Đã cập nhật trạng thái đơn hàng.');
    }

    function showNotice(message, error = false) {
        const notice = document.getElementById('orders-notice');
        notice.textContent = message;
        notice.classList.toggle('error', error);
        notice.hidden = false;
    }

    async function responseMessage(response, fallback) {
        try { return (await response.json()).message || fallback; } catch (_) { return fallback; }
    }
    function formatMoney(value) { return `${new Intl.NumberFormat('vi-VN').format(value || 0)} VND`; }
    function formatDate(value) { return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '-'; }
})();
