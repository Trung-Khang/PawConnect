(() => {
    const token = localStorage.getItem('pawconnect.accessToken');
    const orderId = window.location.pathname.match(/^\/orders\/(\d+)\/receipt$/)?.[1];
    const statusLabels = { PAYMENT_REPORTED: 'Chờ chi nhánh xác nhận thanh toán', PROCESSING: 'Đang xử lý', PAID: 'Đang xử lý', CONFIRMED: 'Đang xử lý', PREPARING: 'Đang xử lý', SHIPPING: 'Đang giao', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã hủy' };

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
        const paid = ['PAID', 'REFUNDED'].includes(order.paymentStatus);
        setText('receipt-heading-title', paid ? 'Biên lai đơn hàng' : 'Thông tin đơn hàng');
        document.title = `${paid ? 'Biên lai' : 'Đơn hàng'} #${order.id} | PawConnect`;
        setText('receipt-number', `Mã đơn #${order.id}`);
        setText('receipt-time', `Ngày đặt: ${formatDate(order.createdAt)}`);
        setText('receipt-customer', `Người đặt: ${order.customerName || 'Chưa ghi nhận'}`);
        setText('receipt-recipient', `Người nhận: ${order.recipientName || 'Chưa ghi nhận'}`);
        setText('receipt-phone', `Số điện thoại: ${order.recipientPhone || 'Chưa ghi nhận'}`);
        setText('receipt-delivery-address', `Địa chỉ nhận: ${order.deliveryAddress || 'Chưa ghi nhận'}`);
        setText('receipt-branch', order.branchName || `Chi nhánh #${order.branchId}`);
        setText('receipt-branch-address', order.branchAddress || 'Chưa ghi nhận địa chỉ');
        setText('receipt-branch-phone', order.branchPhone ? `Điện thoại: ${order.branchPhone}` : '');
        setText('receipt-payment-method', `Phương thức: ${order.paymentMethod === 'SIMULATED' ? 'Thanh toán xác nhận bởi chi nhánh' : 'Chưa ghi nhận'}`);
        setText('receipt-payment-status', `Trạng thái thanh toán: ${paymentLabel(order.paymentStatus)}`);
        setText('receipt-order-status', `Trạng thái đơn: ${statusLabels[order.status] || order.status}`);
        const history = Array.isArray(order.statusHistory) ? order.statusHistory : [];
        const customerEvent = history.find(event => event.status === 'PAYMENT_REPORTED');
        const sellerEvent = history.find(event => event.paymentStatus === 'PAID'
            && ['PROCESSING', 'PAID', 'CONFIRMED', 'PREPARING'].includes(event.status));
        if (customerEvent) {
            setText('receipt-customer-confirmed', `Khách xác nhận: ${formatDate(customerEvent.createdAt)}`);
            document.getElementById('receipt-customer-confirmed').hidden = false;
            setText('receipt-seller-confirmed', sellerEvent
                ? `Chi nhánh xác nhận đã nhận: ${formatDate(sellerEvent.createdAt)}`
                : 'Chi nhánh chưa xác nhận đã nhận thanh toán.');
            document.getElementById('receipt-seller-confirmed').hidden = false;
        }
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
        if (order.couponCode) {
            document.getElementById('receipt-discount-summary').hidden = false;
            setText('receipt-subtotal', formatMoney(order.subtotalAmount));
            setText('receipt-coupon', `Giảm giá (${order.couponCode})`);
            setText('receipt-discount', `−${formatMoney(order.discountAmount)}`);
        }
        document.getElementById('receipt').hidden = false;
        document.getElementById('print-receipt').hidden = !paid;
    }

    function paymentLabel(status) {
        if (status === 'PAID') return 'Chi nhánh đã ghi nhận thanh toán';
        if (status === 'REFUNDED') return 'Đã hoàn tiền';
        if (status === 'CUSTOMER_CONFIRMED') return 'Khách đã xác nhận, chờ chi nhánh xác nhận đã nhận';
        if (status === 'CANCELLED') return 'Đã hủy trước khi ghi nhận thanh toán';
        return 'Chưa ghi nhận';
    }
    function setText(id, text) { document.getElementById(id).textContent = text; }
    function formatMoney(value) { return `${new Intl.NumberFormat('vi-VN').format(value || 0)} VND`; }
    function formatDate(value) { return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—'; }
    function showNotice(message, error = false) { const notice = document.getElementById('receipt-notice'); notice.textContent = message; notice.classList.toggle('error', error); notice.hidden = false; }
})();
