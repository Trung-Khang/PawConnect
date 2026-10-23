(() => {
    const tokenKey = 'pawconnect.accessToken';
    let branches = [];
    let editingId = null;

    document.addEventListener('DOMContentLoaded', init);

    async function init() {
        document.getElementById('reload-branches').addEventListener('click', load);
        document.getElementById('new-branch').addEventListener('click', () => openDialog());
        document.getElementById('cancel-branch').addEventListener('click', () => document.getElementById('branch-dialog').close());
        document.getElementById('branch-form').addEventListener('submit', save);
        try {
            const me = await fetch('/api/auth/me', { headers: authHeaders() });
            if (!me.ok || (await me.json()).role !== 'ADMIN') {
                showNotice('Trang này chỉ dành cho Quản trị viên.', true);
                return;
            }
            document.getElementById('branch-heading-actions').hidden = false;
            document.getElementById('branch-table').hidden = false;
            await load();
        } catch (_) { showNotice('Không thể xác minh tài khoản. Vui lòng đăng nhập lại.', true); }
    }

    async function load() {
        try {
            const response = await fetch('/api/admin/branches', { headers: authHeaders() });
            if (!response.ok) throw new Error(await responseMessage(response, 'Không thể tải chi nhánh.'));
            branches = await response.json();
            document.getElementById('branch-total').textContent = `${branches.length} chi nhánh`;
            const body = document.getElementById('branches-body');
            body.replaceChildren();
            for (const branch of branches) {
                const row = document.createElement('tr');
                row.innerHTML = '<td></td><td></td><td></td><td></td><td class="branch-row-actions"><button class="edit-branch" type="button">Sửa</button><button class="delete-branch" type="button">Xóa</button></td>';
                [branch.code || '', branch.name, branch.address, branch.phone || '—'].forEach((value, index) => { row.children[index].textContent = value; });
                row.querySelector('.edit-branch').addEventListener('click', () => openDialog(branch));
                row.querySelector('.delete-branch').addEventListener('click', () => remove(branch));
                body.append(row);
            }
        } catch (error) { showNotice(error.message, true); }
    }

    function openDialog(branch = null) {
        editingId = branch?.id || null;
        document.getElementById('branch-dialog-title').textContent = branch ? 'Sửa chi nhánh' : 'Thêm chi nhánh';
        document.getElementById('branch-code').value = branch?.code || '';
        document.getElementById('branch-code').disabled = Boolean(branch);
        document.getElementById('branch-name').value = branch?.name || '';
        document.getElementById('branch-address').value = branch?.address || '';
        document.getElementById('branch-phone').value = branch?.phone || '';
        document.getElementById('branch-latitude').value = branch?.latitude ?? '';
        document.getElementById('branch-longitude').value = branch?.longitude ?? '';
        document.getElementById('branch-dialog').showModal();
    }

    async function save(event) {
        event.preventDefault();
        const payload = {
            name: document.getElementById('branch-name').value.trim(),
            address: document.getElementById('branch-address').value.trim(),
            phone: document.getElementById('branch-phone').value.trim(),
            latitude: optionalNumber('branch-latitude'),
            longitude: optionalNumber('branch-longitude')
        };
        if (!editingId) payload.code = document.getElementById('branch-code').value.trim().toUpperCase();
        try {
            const response = await fetch(editingId ? `/api/admin/branches/${editingId}` : '/api/admin/branches', {
                method: editingId ? 'PATCH' : 'POST',
                headers: { ...authHeaders(), 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!response.ok) throw new Error(await responseMessage(response, 'Không thể lưu chi nhánh.'));
            document.getElementById('branch-dialog').close();
            showNotice('Đã lưu chi nhánh.');
            await load();
        } catch (error) { showNotice(error.message, true); }
    }

    async function remove(branch) {
        if (!window.confirm(`Xóa chi nhánh ${branch.name}? Chỉ chi nhánh chưa có dữ liệu liên quan mới xóa được.`)) return;
        try {
            const response = await fetch(`/api/admin/branches/${branch.id}`, { method: 'DELETE', headers: authHeaders() });
            if (!response.ok) throw new Error(await responseMessage(response, 'Không thể xóa chi nhánh.'));
            showNotice('Đã xóa chi nhánh.');
            await load();
        } catch (error) { showNotice(error.message, true); }
    }

    function optionalNumber(id) { const value = document.getElementById(id).value; return value === '' ? null : Number(value); }
    function authHeaders() { const token = localStorage.getItem(tokenKey); return token ? { Authorization: `Bearer ${token}` } : {}; }
    async function responseMessage(response, fallback) { try { return (await response.json()).message || fallback; } catch (_) { return fallback; } }
    function showNotice(message, error = false) { const notice = document.getElementById('branch-notice'); notice.textContent = message; notice.classList.toggle('error', error); notice.hidden = false; }
})();
