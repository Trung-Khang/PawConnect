(() => {
    const tokenKey = 'pawconnect.accessToken';
    const statusLabels = {
        PENDING: 'Chờ xác nhận',
        CONFIRMED: 'Đã xác nhận',
        ARRIVED: 'Khách đã tới',
        COMPLETED: 'Đã hoàn thành',
        NO_SHOW: 'Khách không tới',
        CANCELLED: 'Đã từ chối / Đã hủy'
    };

    let allBookings = [];
    let currentUser = null;

    document.addEventListener('DOMContentLoaded', async () => {
        document.getElementById('reload-services').addEventListener('click', loadBookings);
        document.getElementById('status-filter').addEventListener('change', filterAndRender);
        document.getElementById('branch-filter').addEventListener('change', filterAndRender);

        try {
            const meRes = await fetch('/api/auth/me', { headers: authHeaders() });
            if (!meRes.ok) return showNotice('Vui lòng đăng nhập bằng tài khoản quản lý để xem lịch hẹn.', true);
            currentUser = await meRes.json();
            if (!['ADMIN', 'BRANCH_MANAGER'].includes(currentUser.role)) {
                return showNotice('Bạn không có quyền quản lý lịch hẹn dịch vụ.', true);
            }

            if (currentUser.role === 'ADMIN') {
                const branchFilter = document.getElementById('branch-filter');
                branchFilter.hidden = false;
                await loadBranches(branchFilter);
            }

            await loadBookings();
        } catch (_) {
            showNotice('Không thể xác minh tài khoản.', true);
        }
    });

    function authHeaders() {
        const token = localStorage.getItem(tokenKey);
        return token ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } : { 'Content-Type': 'application/json' };
    }

    async function loadBranches(selectEl) {
        try {
            const res = await fetch('/api/branches');
            if (res.ok) {
                const branches = await res.json();
                branches.forEach(b => {
                    const opt = document.createElement('option');
                    opt.value = b.id;
                    opt.textContent = b.name;
                    selectEl.appendChild(opt);
                });
            }
        } catch (e) {
            console.error('Không thể tải danh sách chi nhánh', e);
        }
    }

    async function loadBookings() {
        showNotice('Đang tải danh sách lịch hẹn…');
        try {
            const response = await fetch('/api/bookings/manage', { headers: authHeaders() });
            if (!response.ok) {
                const error = await response.json().catch(() => ({}));
                throw new Error(error.message || 'Không thể tải danh sách lịch hẹn dịch vụ.');
            }
            allBookings = await response.json();
            hideNotice();
            filterAndRender();
        } catch (error) {
            showNotice(error.message, true);
        }
    }

    function filterAndRender() {
        const statusVal = document.getElementById('status-filter').value;
        const branchVal = document.getElementById('branch-filter').value;

        let filtered = allBookings;
        if (statusVal) {
            filtered = filtered.filter(b => b.status === statusVal);
        }
        if (branchVal) {
            filtered = filtered.filter(b => String(b.branchId) === String(branchVal));
        }

        const body = document.getElementById('services-table-body');
        body.replaceChildren();

        if (!filtered.length) {
            body.innerHTML = '<tr><td colspan="8" style="text-align:center; padding: 2rem; color: var(--text-muted);">Không có lịch hẹn nào phù hợp.</td></tr>';
            return;
        }

        filtered.forEach(b => body.appendChild(renderRow(b)));
    }

    function renderRow(booking) {
        const tr = document.createElement('tr');

        // Customer info
        const custName = booking.customerName || `Khách hàng #${booking.userId}`;
        const custPhone = booking.customerPhone ? `<br><small style="color:var(--text-muted);"><i class="fas fa-phone"></i> ${booking.customerPhone}</small>` : '';
        const custEmail = booking.customerEmail ? `<br><small style="color:var(--text-muted);"><i class="fas fa-envelope"></i> ${booking.customerEmail}</small>` : '';

        // Service info
        const sName = booking.serviceTypeName || 'Dịch vụ thú cưng';
        const sPrice = booking.servicePrice ? `<br><small style="color:var(--primary); font-weight:600;">${formatMoney(booking.servicePrice)}</small>` : '';
        const sDuration = booking.durationMinutes ? `<br><small style="color:var(--text-muted);"><i class="fas fa-clock"></i> ${booking.durationMinutes} phút</small>` : '';

        // Status badge & reason
        const statusText = statusLabels[booking.status] || booking.status;
        const reasonHtml = booking.reason ? `<div class="reason-box"><strong>Lý do:</strong> ${escapeHtml(booking.reason)}</div>` : '';

        tr.innerHTML = `
            <td data-label="Mã lịch"><strong>#${booking.id}</strong></td>
            <td data-label="Khách hàng"><strong>${escapeHtml(custName)}</strong>${custPhone}${custEmail}</td>
            <td data-label="Dịch vụ"><strong>${escapeHtml(sName)}</strong>${sPrice}${sDuration}</td>
            <td data-label="Chi nhánh">${escapeHtml(booking.branchName || `Chi nhánh #${booking.branchId}`)}</td>
            <td data-label="Ngày & giờ hẹn"><strong style="color:#d35400;">${formatDateTime(booking.bookingTime)}</strong></td>
            <td data-label="Thời điểm đặt"><small style="color:var(--text-muted);">${formatDateTime(booking.createdAt)}</small></td>
            <td data-label="Trạng thái">
                <span class="status-badge ${booking.status}">${statusText}</span>
                ${reasonHtml}
            </td>
            <td class="action-cell" data-label="Thao tác"></td>
        `;

        const actionCell = tr.querySelector('.action-cell');
        buildActions(actionCell, booking);

        return tr;
    }

    function buildActions(container, booking) {
        const status = booking.status;

        if (status === 'PENDING') {
            const btnConfirm = createBtn('Xác nhận lịch hẹn', 'btn-confirm', 'fa-check', () => updateStatus(booking.id, 'CONFIRMED'));
            const btnReject = createBtn('Từ chối', 'btn-reject', 'fa-xmark', () => rejectBooking(booking.id));
            container.append(btnConfirm, btnReject);
        } else if (status === 'CONFIRMED') {
            const btnArrive = createBtn('Khách đã tới', 'btn-arrive', 'fa-user-check', () => updateStatus(booking.id, 'ARRIVED'));
            const btnNoShow = createBtn('Khách không tới', 'btn-noshow', 'fa-user-slash', () => {
                if (confirm(`Xác nhận khách #${booking.id} không đến hẹn?`)) {
                    updateStatus(booking.id, 'NO_SHOW');
                }
            });
            const btnReject = createBtn('Hủy hẹn', 'btn-reject', 'fa-xmark', () => rejectBooking(booking.id));
            container.append(btnArrive, btnNoShow, btnReject);
        } else if (status === 'ARRIVED') {
            const btnComplete = createBtn('Hoàn thành', 'btn-complete', 'fa-check-double', () => updateStatus(booking.id, 'COMPLETED'));
            container.append(btnComplete);
        } else {
            const spanDone = document.createElement('span');
            spanDone.style.color = 'var(--text-muted)';
            spanDone.style.fontSize = '0.85rem';
            spanDone.textContent = '—';
            container.append(spanDone);
        }
    }

    function createBtn(text, extraClass, iconClass, onClick) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = `service-action-btn ${extraClass}`;
        btn.innerHTML = `<i class="fas ${iconClass}"></i> ${text}`;
        btn.addEventListener('click', onClick);
        return btn;
    }

    async function updateStatus(id, newStatus, reason = null) {
        try {
            showNotice('Đang cập nhật trạng thái…');
            let url = `/api/bookings/${id}/status?status=${encodeURIComponent(newStatus)}`;
            if (reason) url += `&reason=${encodeURIComponent(reason)}`;

            const res = await fetch(url, {
                method: 'PUT',
                headers: authHeaders(),
                body: reason ? JSON.stringify({ reason }) : undefined
            });

            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                throw new Error(err.message || 'Không thể cập nhật trạng thái.');
            }

            showNotice(`Đã cập nhật lịch #${id} thành công!`);
            await loadBookings();
        } catch (e) {
            showNotice(e.message, true);
        }
    }

    function promptRejectServiceModal({ title, subtitle, defaultReason, quickTags }) {
        return new Promise((resolve) => {
            const overlay = document.createElement('div');
            overlay.className = 'frosted-modal-overlay';

            const card = document.createElement('div');
            card.className = 'frosted-glass-card';

            const tagsHtml = (quickTags || []).map(tag =>
                `<button type="button" class="frosted-tag-btn">${escapeHtml(tag)}</button>`
            ).join('');

            card.innerHTML = `
                <button type="button" class="frosted-modal-close" aria-label="Đóng"><i class="fas fa-xmark"></i></button>
                <div class="frosted-modal-header">
                    <div class="frosted-modal-icon">
                        <i class="fas fa-calendar-xmark"></i>
                    </div>
                    <div>
                        <h3 class="frosted-modal-title">${escapeHtml(title || 'Từ chối lịch hẹn')}</h3>
                        <p class="frosted-modal-desc">${escapeHtml(subtitle || 'Nhập lý do gửi đến khách hàng')}</p>
                    </div>
                </div>
                ${tagsHtml ? `<div class="frosted-quick-tags">${tagsHtml}</div>` : ''}
                <textarea class="frosted-textarea" placeholder="Nhập lý do chi tiết gửi đến khách hàng...">${escapeHtml(defaultReason || '')}</textarea>
                <div class="frosted-modal-actions">
                    <button type="button" class="frosted-btn-cancel">Hủy bỏ</button>
                    <button type="button" class="frosted-btn-confirm">Xác nhận từ chối</button>
                </div>
            `;

            overlay.appendChild(card);
            document.body.appendChild(overlay);

            requestAnimationFrame(() => overlay.classList.add('active'));

            const textarea = card.querySelector('.frosted-textarea');
            textarea.focus();
            textarea.select();

            card.querySelectorAll('.frosted-tag-btn').forEach(btn => {
                btn.addEventListener('click', () => {
                    textarea.value = btn.textContent;
                    textarea.focus();
                });
            });

            function cleanup(result) {
                overlay.classList.remove('active');
                setTimeout(() => {
                    overlay.remove();
                    resolve(result);
                }, 250);
            }

            card.querySelector('.frosted-modal-close').addEventListener('click', () => cleanup(null));
            card.querySelector('.frosted-btn-cancel').addEventListener('click', () => cleanup(null));
            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) cleanup(null);
            });

            card.querySelector('.frosted-btn-confirm').addEventListener('click', () => {
                const val = textarea.value.trim();
                if (!val) {
                    alert('Vui lòng nhập lý do từ chối.');
                    textarea.focus();
                    return;
                }
                cleanup(val);
            });
        });
    }

    async function rejectBooking(id) {
        const reason = await promptRejectServiceModal({
            title: 'Từ chối / Hủy lịch hẹn',
            subtitle: 'Nhập lý do từ chối để hệ thống phản hồi tới khách hàng đã đặt lịch.',
            defaultReason: 'Chi nhánh đã kín lịch hẹn vào thời điểm này',
            quickTags: [
                'Chi nhánh đã kín lịch hẹn vào thời điểm này',
                'Chi nhánh bảo trì trang thiết bị dịch vụ',
                'Khách hàng yêu cầu dời hoặc hủy lịch',
                'Nhân viên kỹ thuật bận ca đột xuất'
            ]
        });
        if (reason === null) return;
        updateStatus(id, 'CANCELLED', reason);
    }

    function formatDateTime(val) {
        if (!val) return '—';
        const d = new Date(val);
        if (isNaN(d.getTime())) return val;
        return new Intl.DateTimeFormat('vi-VN', {
            dateStyle: 'short',
            timeStyle: 'short'
        }).format(d);
    }

    function formatMoney(value) {
        return `${new Intl.NumberFormat('vi-VN').format(value || 0)} VND`;
    }

    function escapeHtml(text) {
        if (!text) return '';
        const d = document.createElement('div');
        d.textContent = text;
        return d.innerHTML;
    }

    function showNotice(msg, isError = false) {
        const el = document.getElementById('services-notice');
        el.textContent = msg;
        el.classList.toggle('error', isError);
        el.hidden = false;
    }

    function hideNotice() {
        document.getElementById('services-notice').hidden = true;
    }
})();
