(() => {
    const staffSteps = [
        { status: 'PAYMENT_REPORTED', label: 'Chờ xác nhận thanh toán', detail: 'Khách đã xác nhận; chi nhánh cần kiểm tra thanh toán và đơn.' },
        { status: 'PROCESSING', label: 'Đang xử lý', detail: 'Chi nhánh đã nhận thanh toán và đang chuẩn bị đơn.' },
        { status: 'SHIPPING', label: 'Đang giao hàng', detail: 'Đơn đang được giao tới người nhận.' },
        { status: 'COMPLETED', label: 'Hoàn tất', detail: 'Đơn đã hoàn thành.' }
    ];
    const customerSteps = staffSteps.map(step => ({ ...step }));
    const labels = Object.fromEntries(staffSteps.map(step => [step.status, step.label]));
    for (const legacy of ['PAID', 'CONFIRMED', 'PREPARING']) labels[legacy] = 'Đang xử lý';
    labels.CANCELLED = 'Đã hủy';

    function formatDate(value) {
        if (!value) return 'Chưa có thời gian';
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? 'Chưa có thời gian'
            : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(date);
    }

    function eventTime(event, prefix) {
        const row = document.createElement('p');
        row.className = 'order-progress-event';
        row.append(document.createTextNode(`${prefix}: `));
        const time = document.createElement('time');
        time.dateTime = event.createdAt;
        time.textContent = formatDate(event.createdAt);
        row.append(time);
        return row;
    }

    function paymentConfirmation(history, cancelled) {
        const customerEvent = history.find(event => event.status === 'PAYMENT_REPORTED');
        if (!customerEvent) return null;
        const sellerEvent = history.find(event => event.paymentStatus === 'PAID'
            && ['PROCESSING', 'PAID', 'CONFIRMED', 'PREPARING'].includes(event.status));
        const box = document.createElement('div');
        box.className = 'order-payment-confirmation';
        const heading = document.createElement('strong');
        heading.textContent = 'Xác nhận thanh toán';
        box.append(heading, eventTime(customerEvent, 'Khách xác nhận'));
        if (sellerEvent) box.append(eventTime(sellerEvent, 'Chi nhánh xác nhận đã nhận'));
        else {
            const pending = document.createElement('p');
            pending.textContent = cancelled ? 'Chi nhánh chưa ghi nhận thanh toán trước khi hủy.'
                : 'Đang chờ chi nhánh xác nhận đã nhận thanh toán.';
            box.append(pending);
        }
        return box;
    }

    function render(order, options = {}) {
        const customerView = options.customerView === true;
        const steps = customerView ? customerSteps : staffSteps;
        const wrapper = document.createElement('section');
        wrapper.className = `order-progress${customerView ? ' is-customer' : ''}`;
        wrapper.setAttribute('aria-label', `Tiến trình đơn hàng #${order.id}`);
        const title = document.createElement('h3');
        title.className = 'order-progress-title';
        title.textContent = 'Tiến trình xử lý';
        wrapper.append(title);
        const history = Array.isArray(order.statusHistory) ? order.statusHistory : [];
        const cancelled = order.status === 'CANCELLED';
        const payment = paymentConfirmation(history, cancelled);
        if (payment) wrapper.append(payment);
        const currentStatus = ['PAID', 'CONFIRMED', 'PREPARING'].includes(order.status)
            ? 'PROCESSING' : order.status;
        const currentIndex = steps.findIndex(step => step.status === currentStatus);
        const list = document.createElement('ol');
        list.className = 'order-progress-list';
        steps.forEach((step, index) => {
            const item = document.createElement('li');
            const matchingEvents = step.status === 'PROCESSING'
                ? history.filter(entry => ['PROCESSING', 'PAID', 'CONFIRMED', 'PREPARING'].includes(entry.status))
                : history.filter(entry => entry.status === step.status);
            const reached = matchingEvents.length > 0 || (!cancelled && currentIndex >= index && currentIndex !== -1);
            item.className = `order-progress-step ${reached ? 'is-done' : 'is-upcoming'}`;
            if (!cancelled && currentIndex === index) {
                item.classList.add('is-current');
                item.setAttribute('aria-current', 'step');
            }
            const marker = document.createElement('span');
            marker.className = 'order-progress-marker';
            marker.textContent = reached ? '✓' : String(index + 1);
            marker.setAttribute('aria-hidden', 'true');
            const content = document.createElement('div');
            const name = document.createElement('strong');
            name.textContent = step.label;
            const description = document.createElement('p');
            description.textContent = reached ? step.detail : 'Chưa tới bước này';
            content.append(name, description);
            matchingEvents.forEach(event => {
                if (event.createdAt) content.append(eventTime(event,
                    step.status === 'PROCESSING' && event.status === 'PREPARING' ? 'Bắt đầu chuẩn bị (lịch sử cũ)' : 'Thời điểm'));
                if (event.reason && !['PROCESSING', 'PAID', 'PAYMENT_REPORTED'].includes(event.status)
                    && event.reason !== 'Chi nhánh cập nhật trạng thái đơn') {
                    const note = document.createElement('p');
                    note.textContent = `Ghi chú: ${event.reason}`;
                    content.append(note);
                }
            });
            item.append(marker, content);
            list.append(item);
        });
        if (cancelled) {
            const item = document.createElement('li');
            item.className = 'order-progress-step is-cancelled';
            item.setAttribute('aria-current', 'step');
            const marker = document.createElement('span');
            marker.className = 'order-progress-marker';
            marker.textContent = '×';
            marker.setAttribute('aria-hidden', 'true');
            const content = document.createElement('div');
            const name = document.createElement('strong');
            name.textContent = order.paymentStatus === 'REFUNDED' ? 'Đã hủy và hoàn tiền' : 'Đã hủy đơn';
            content.append(name);
            const event = history.find(entry => entry.status === 'CANCELLED');
            if (event?.createdAt) content.append(eventTime(event, 'Thời điểm'));
            if (event?.reason) {
                const reason = document.createElement('p');
                reason.textContent = `Lý do: ${event.reason}`;
                content.append(reason);
            }
            item.append(marker, content);
            list.append(item);
        }
        wrapper.append(list);
        const requests = Array.isArray(order.cancellationRequests) ? order.cancellationRequests : [];
        if (requests.length) {
            const heading = document.createElement('h4');
            heading.className = 'order-progress-title mt-3';
            heading.textContent = 'Yêu cầu hủy';
            wrapper.append(heading);
            requests.forEach(request => {
                const box = document.createElement('div');
                box.className = 'order-payment-confirmation';
                const state = document.createElement('strong');
                state.textContent = request.status === 'PENDING' ? 'Đang chờ chi nhánh trả lời'
                    : request.status === 'APPROVED' ? 'Chi nhánh đã chấp nhận hủy' : 'Chi nhánh đã từ chối hủy';
                box.append(state);
                const reason = document.createElement('p');
                reason.textContent = `Lý do khách gửi: ${request.reason}`;
                box.append(reason);
                if (request.requestedAt) box.append(eventTime({ createdAt: request.requestedAt }, 'Gửi lúc'));
                if (request.resolvedAt) box.append(eventTime({ createdAt: request.resolvedAt }, 'Phản hồi lúc'));
                if (request.decisionReason) {
                    const decision = document.createElement('p');
                    decision.textContent = `Lý do chi nhánh: ${request.decisionReason}`;
                    box.append(decision);
                }
                wrapper.append(box);
            });
        }
        return wrapper;
    }

    window.PawConnectOrderProgress = { render, labels, staffSteps, customerSteps, formatDate };
})();
