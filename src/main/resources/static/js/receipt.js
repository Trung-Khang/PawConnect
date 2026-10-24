(() => {
    const token = localStorage.getItem('pawconnect.accessToken');
    const orderId = window.location.pathname.match(/^\/orders\/(\d+)\/receipt$/)?.[1];
    const statusLabels = { PAID: 'Đã thanh toán', CONFIRMED: 'Đã xác nhận', PREPARING: 'Đang chuẩn bị', SHIPPING: 'Đang giao', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã hủy', PENDING: 'Chờ xác nhận', SHIPPED: 'Đang giao', DELIVERED: 'Đã giao' };

    document.addEventListener('DOMContentLoaded', async () => {
        document.getElementById('print-receipt').addEventListener('click', () => window.print());
        if (!token) return window.location.assign(`/auth/login?returnTo=${encodeURIComponent(window.location.pathname)}`);
        if (!orderId) return showNotice('Đường dẫn biên lai không hợp lệ.', true);
        try {
            const response = await fetch(`/api/orders/${orderId}`, { headers: { Authorization: `Bearer ${token}` } });
            if (!response.ok) {
                const data = await response.json().catch(() => ({}));
                throw new Error(response.status === 403 ? 'Bạn không có quyền xem biên lai này.' : data.message || 'Không thể tải biên lai.');
            }
            render(await response.json());
        } catch (error) { showNotice(error.message, true); }
    });

    function render(order) {
        setText('receipt-number', `Mã đơn #${order.id}`);
        setText('receipt-time', `Ngày đặt: ${formatDate(order.createdAt)}`);
        setText('receipt-customer', `Người đặt: ${order.customerName || 'Chưa ghi nhận'}`);
        setText('receipt-recipient', `Người nhận: ${order.recipientName || 'Chưa ghi nhận'}`);
        setText('receipt-phone', `Số điện thoại: ${order.recipientPhone || 'Chưa ghi nhận'}`);
        setText('receipt-delivery-address', `Địa chỉ nhận: ${order.deliveryAddress || 'Chưa ghi nhận'}`);
        setText('receipt-branch', order.branchName || `Chi nhánh #${order.branchId}`);
        setText('receipt-branch-address', order.branchAddress || 'Chưa ghi nhận địa chỉ');
        setText('receipt-branch-phone', order.branchPhone ? `Điện thoại: ${order.branchPhone}` : '');
        setText('receipt-payment-method', `Phương thức: ${order.paymentMethod === 'SIMULATED' ? 'Thanh toán trực tuyến' : 'Chưa ghi nhận'}`);
        setText('receipt-payment-status', `Trạng thái thanh toán: ${order.paymentStatus === 'REFUNDED' ? 'Đã hoàn tiền' : order.paymentStatus === 'PAID' ? 'Đã thanh toán' : 'Chưa ghi nhận'}`);
        setText('receipt-order-status', `Trạng thái đơn: ${statusLabels[order.status] || order.status}`);
        const body = document.getElementById('receipt-items');
        body.replaceChildren();
        (order.items || []).forEach(item => {
            const row = document.createElement('tr');
            for (const text of [item.productName, String(item.quantity), formatMoney(item.price), formatMoney(Number(item.price) * item.quantity)]) {
                const cell = document.createElement('td');
                cell.textContent = text;
                if (row.children.length) cell.className = 'text-end';
                row.append(cell);
            }
            body.append(row);
        });
        setText('receipt-total', formatMoney(order.totalAmount));
        document.getElementById('receipt').hidden = false;
        document.getElementById('print-receipt').hidden = false;
    }

    function setText(id, text) { document.getElementById(id).textContent = text; }
    function formatMoney(value) { return `${new Intl.NumberFormat('vi-VN').format(value || 0)} VND`; }
    function formatDate(value) { return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—'; }
    function showNotice(message, error = false) { const notice = document.getElementById('receipt-notice'); notice.textContent = message; notice.classList.toggle('error', error); notice.hidden = false; }
})();
