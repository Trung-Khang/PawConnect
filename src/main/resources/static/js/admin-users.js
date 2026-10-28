(function () {
    const tokenKey = 'pawconnect.accessToken';
    const state = { currentUserId: null, users: [] };

    document.addEventListener('DOMContentLoaded', initialise);

    async function initialise() {
        bindEvents();
        const currentUser = await requestCurrentUser();
        if (!currentUser || currentUser.role !== 'ADMIN') {
            showNotice('Trang này chỉ dành cho Quản trị viên.', true);
            return;
        }
        state.currentUserId = currentUser.id;
        document.getElementById('reload-users').hidden = false;
        document.getElementById('admin-users-table').hidden = false;
        await loadUsers();
    }

    function bindEvents() {
        document.getElementById('reload-users')?.addEventListener('click', loadUsers);
        document.getElementById('edit-user-form')?.addEventListener('submit', saveUser);
        document.querySelectorAll('[data-close-user-modal]').forEach((element) => element.addEventListener('click', closeModal));
    }

    async function requestCurrentUser() {
        const token = localStorage.getItem(tokenKey);
        if (!token) return null;
        const response = await fetch('/api/auth/me', { headers: authHeaders() });
        return response.ok ? response.json() : null;
    }

    async function loadUsers() {
        const response = await fetch('/api/admin/users', { headers: authHeaders() });
        if (!response.ok) return showNotice('Không thể tải danh sách tài khoản.', true);
        state.users = await response.json();
        document.getElementById('account-total').textContent = `${state.users.length} tài khoản`;
        renderUsers();
    }

    function renderUsers() {
        const body = document.getElementById('admin-users-table-body');
        body.replaceChildren();
        state.users.forEach((user) => {
            const row = document.createElement('tr');
            const disabledSelf = user.id === state.currentUserId;
            row.innerHTML = `
                <td data-label="Tài khoản"><strong></strong><small></small></td>
                <td data-label="Email"></td><td data-label="Số điện thoại"></td><td data-label="Vai trò"><span class="role-badge"></span></td>
                <td data-label="Trạng thái"><span class="account-state"></span></td>
                <td class="admin-user-actions" data-label="Thao tác"><button type="button" class="icon-btn edit-user" title="Chỉnh sửa" aria-label="Chỉnh sửa"><i class="fas fa-pen"></i></button><button type="button" class="icon-btn delete-user" title="Xóa tài khoản" aria-label="Xóa tài khoản"><i class="fas fa-trash"></i></button></td>`;
            row.querySelector('strong').textContent = user.fullName;
            row.querySelector('small').textContent = user.username || 'Tài khoản seed';
            row.children[1].textContent = user.email;
            row.children[2].textContent = user.phone || 'Chưa cập nhật';
            row.querySelector('.role-badge').textContent = roleLabel(user.role);
            row.querySelector('.account-state').textContent = user.locked ? 'Đã khóa' : user.active ? 'Đang hoạt động' : 'Đã vô hiệu hóa';
            row.querySelector('.account-state').classList.add(user.locked || !user.active ? 'inactive' : 'active');
            row.querySelector('.edit-user').addEventListener('click', () => openModal(user));
            const deleteButton = row.querySelector('.delete-user');
            deleteButton.disabled = disabledSelf;
            deleteButton.addEventListener('click', () => deleteUser(user));
            body.append(row);
        });
    }

    function openModal(user) {
        document.getElementById('edit-user-id').value = user.id;
        document.getElementById('edit-user-email').value = user.email;
        document.getElementById('edit-user-full-name').value = user.fullName;
        document.getElementById('edit-user-phone').value = user.phone || '';
        document.getElementById('edit-user-active').checked = user.active;
        document.getElementById('edit-user-locked').checked = user.locked;
        const isSelf = user.id === state.currentUserId;
        document.getElementById('edit-user-active').disabled = isSelf;
        document.getElementById('edit-user-locked').disabled = isSelf;
        document.getElementById('user-edit-modal').hidden = false;
    }

    function closeModal() { document.getElementById('user-edit-modal').hidden = true; }

    async function saveUser(event) {
        event.preventDefault();
        const id = document.getElementById('edit-user-id').value;
        const response = await fetch(`/api/admin/users/${encodeURIComponent(id)}`, {
            method: 'PATCH', headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify({
                fullName: document.getElementById('edit-user-full-name').value.trim(),
                phone: document.getElementById('edit-user-phone').value.trim(),
                active: document.getElementById('edit-user-active').checked,
                locked: document.getElementById('edit-user-locked').checked
            })
        });
        if (!response.ok) return showNotice(await responseMessage(response, 'Không thể cập nhật tài khoản.'), true);
        closeModal();
        showNotice('Đã cập nhật tài khoản.');
        await loadUsers();
    }

    async function deleteUser(user) {
        if (!window.confirm(`Xóa tài khoản ${user.email}? Thao tác này không thể hoàn tác.`)) return;
        const response = await fetch(`/api/admin/users/${encodeURIComponent(user.id)}`, { method: 'DELETE', headers: authHeaders() });
        if (!response.ok) return showNotice(await responseMessage(response, 'Không thể xóa tài khoản.'), true);
        showNotice('Đã xóa tài khoản.');
        await loadUsers();
    }

    function authHeaders() {
        const token = localStorage.getItem(tokenKey);
        return token ? { Authorization: `Bearer ${token}` } : {};
    }

    async function responseMessage(response, fallback) {
        try { return (await response.json()).message || fallback; } catch (_) { return fallback; }
    }

    function showNotice(message, error = false) {
        const notice = document.getElementById('admin-users-notice');
        notice.textContent = message;
        notice.classList.toggle('error', error);
        notice.hidden = false;
    }

    function roleLabel(role) {
        if (role === 'ADMIN') return 'Quản trị viên';
        if (role === 'BRANCH_MANAGER') return 'Nhân viên chi nhánh';
        return 'Khách hàng';
    }
})();
