(() => {
    const tokenKey = 'pawconnect.accessToken';
    let branches = [];
    let editingId = null;

    document.addEventListener('DOMContentLoaded', init);

    async function init() {
        document.getElementById('reload-branches').addEventListener('click', load);
        document.getElementById('new-branch').addEventListener('click', () => openDialog());
        document.getElementById('cancel-branch').addEventListener('click', () => document.getElementById('branch-dialog').close());
        document.getElementById('close-branch-details').addEventListener('click', () => document.getElementById('branch-details-dialog').close());
        document.getElementById('branch-form').addEventListener('submit', save);

        // Tab switcher in details dialog
        document.querySelectorAll('.branch-tab-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const targetTab = btn.dataset.tab;
                document.querySelectorAll('.branch-tab-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                document.querySelectorAll('.branch-tab-content').forEach(c => {
                    c.hidden = c.id !== targetTab;
                    c.classList.toggle('active', c.id === targetTab);
                });
            });
        });

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

    function formatVnd(val) {
        if (!val || isNaN(val)) return '0 ₫';
        return Number(val).toLocaleString('vi-VN') + ' ₫';
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
                row.innerHTML = `
                    <td data-label="Mã & Tên chi nhánh">
                        <strong class="d-block" style="font-size:1.02rem; color: #2d3748;">${escapeHtml(branch.name)}</strong>
                        <span class="branch-code-badge">${escapeHtml(branch.code || '—')}</span>
                    </td>
                    <td data-label="Địa chỉ & Hotline">
                        <div style="font-size: 0.88rem; max-width: 260px; line-height: 1.35;">${escapeHtml(branch.address || '—')}</div>
                        <small style="color: var(--primary-color); font-weight: 700; display: inline-flex; align-items: center; gap: 0.25rem; margin-top: 0.25rem;">
                            <i class="fas fa-phone-volume"></i> ${escapeHtml(branch.phone || 'Chưa cập nhật')}
                        </small>
                    </td>
                    <td data-label="Tồn kho">
                        <div class="branch-metric-cell">
                            <span class="metric-primary stock-val"><i class="fas fa-boxes-stacked"></i> ${branch.totalStock ?? 0} món</span>
                            <span class="metric-secondary">${branch.productStock ?? 0} SP (${branch.productTypesCount ?? 0} loại) · ${branch.puppyStock ?? 0} cún</span>
                        </div>
                    </td>
                    <td data-label="Nhận nuôi">
                        <div class="branch-metric-cell">
                            <span class="metric-primary adoption-val"><i class="fas fa-paw"></i> ${branch.adoptionTotal ?? 0} hồ sơ</span>
                            <span class="metric-secondary"><i class="fas fa-heart" style="color:#e53e3e"></i> ${branch.adoptionAvailable ?? 0} đang tìm chủ</span>
                        </div>
                    </td>
                    <td data-label="Đã bán & Doanh thu">
                        <div class="branch-metric-cell">
                            <span class="metric-primary sold-val"><i class="fas fa-receipt"></i> ${branch.soldOrdersCount ?? 0} đơn (${branch.totalItemsSold ?? 0} món)</span>
                            <span class="metric-secondary font-monospace fw-bold" style="color: #276749;">${formatVnd(branch.totalRevenue)}</span>
                        </div>
                    </td>
                    <td class="branch-row-actions" data-label="Thao tác">
                        <button class="view-branch" type="button" title="Xem chi tiết tồn kho, nhận nuôi và đơn hàng"><i class="fas fa-eye"></i> Chi tiết</button>
                        <button class="edit-branch" type="button" title="Sửa thông tin chi nhánh"><i class="fas fa-pen"></i> Sửa</button>
                        <button class="delete-branch" type="button" title="Xóa chi nhánh"><i class="fas fa-trash"></i> Xóa</button>
                    </td>
                `;

                row.querySelector('.view-branch').addEventListener('click', () => openDetails(branch));
                row.querySelector('.edit-branch').addEventListener('click', () => openDialog(branch));
                row.querySelector('.delete-branch').addEventListener('click', () => remove(branch));
                body.append(row);
            }
        } catch (error) { showNotice(error.message, true); }
    }

    async function openDetails(branch) {
        const dialog = document.getElementById('branch-details-dialog');
        document.getElementById('details-branch-code').textContent = branch.code || 'BR';
        document.getElementById('branch-details-title').textContent = branch.name;
        document.getElementById('details-branch-address').textContent = `${branch.address} · Hotline: ${branch.phone || 'Chưa cập nhật'}`;

        document.getElementById('modal-metric-stock').textContent = `${branch.totalStock ?? 0} món`;
        document.getElementById('modal-metric-stock-desc').textContent = `${branch.productStock ?? 0} sản phẩm (${branch.productTypesCount ?? 0} loại) · ${branch.puppyStock ?? 0} cún đang bán`;

        document.getElementById('modal-metric-adoption').textContent = `${branch.adoptionTotal ?? 0} bé`;
        document.getElementById('modal-metric-adoption-desc').textContent = `${branch.adoptionAvailable ?? 0} bé đang mở tìm chủ yêu thương`;

        document.getElementById('modal-metric-orders').textContent = `${branch.soldOrdersCount ?? 0} đơn hàng`;
        document.getElementById('modal-metric-sales-desc').textContent = `${branch.totalItemsSold ?? 0} món đã bán · Doanh thu: ${formatVnd(branch.totalRevenue)}`;

        // Reset tabs to inventory
        document.querySelectorAll('.branch-tab-btn').forEach((b, i) => b.classList.toggle('active', i === 0));
        document.querySelectorAll('.branch-tab-content').forEach((c, i) => {
            c.hidden = i !== 0;
            c.classList.toggle('active', i === 0);
        });

        dialog.showModal();

        // Load detail data from backend
        try {
            const res = await fetch(`/api/admin/branches/${branch.id}/details`, { headers: authHeaders() });
            if (!res.ok) throw new Error('Không thể tải chi tiết danh mục.');
            const data = await res.json();

            // 1. Inventory Products
            const prodBody = document.getElementById('details-products-body');
            prodBody.replaceChildren();
            if (!data.products || !data.products.length) {
                prodBody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">Chưa có sản phẩm nào trong kho chi nhánh này.</td></tr>';
            } else {
                data.products.forEach(p => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                        <td><strong>${escapeHtml(p.name)}</strong></td>
                        <td><span class="status-chip chip-info">${escapeHtml(p.categoryName || 'Phụ kiện')}</span></td>
                        <td>${formatVnd(p.price)}</td>
                        <td><strong class="stock-val">${p.stock}</strong></td>
                    `;
                    prodBody.append(tr);
                });
            }

            // 2. Puppies
            const pupBody = document.getElementById('details-puppies-body');
            pupBody.replaceChildren();
            if (!data.puppies || !data.puppies.length) {
                pupBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Chi nhánh chưa có bài đăng bán cún.</td></tr>';
            } else {
                data.puppies.forEach(pl => {
                    const tr = document.createElement('tr');
                    const statusChip = pl.status === 'AVAILABLE' 
                        ? '<span class="status-chip chip-success">Còn bán</span>' 
                        : '<span class="status-chip chip-danger">Đã hết</span>';
                    tr.innerHTML = `
                        <td><strong>${escapeHtml(pl.listingTitle)}</strong></td>
                        <td><span class="branch-code-badge">${escapeHtml(pl.breedCode || '—')}</span></td>
                        <td>${formatVnd(pl.price)}</td>
                        <td><strong>${pl.stock}</strong></td>
                        <td>${statusChip}</td>
                    `;
                    pupBody.append(tr);
                });
            }

            // 3. Adoptions
            const adoptBody = document.getElementById('details-adoptions-body');
            adoptBody.replaceChildren();
            if (!data.adoptions || !data.adoptions.length) {
                adoptBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Chưa có hồ sơ thú cưng nhận nuôi tại chi nhánh.</td></tr>';
            } else {
                data.adoptions.forEach(dp => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                        <td><strong><i class="fas fa-dog text-warning"></i> ${escapeHtml(dp.name)}</strong></td>
                        <td>${escapeHtml(dp.breed || 'Chó lai')}</td>
                        <td><span class="status-chip chip-warning">${escapeHtml(dp.size || 'MEDIUM')}</span></td>
                        <td>${dp.ageMonths ? dp.ageMonths + ' tháng' : '—'}</td>
                        <td><span class="status-chip chip-success">Cần nhận nuôi</span></td>
                    `;
                    adoptBody.append(tr);
                });
            }

            // 4. Orders
            const ordBody = document.getElementById('details-orders-body');
            ordBody.replaceChildren();
            if (!data.recentOrders || !data.recentOrders.length) {
                ordBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Chưa có đơn hàng nào tại chi nhánh.</td></tr>';
            } else {
                data.recentOrders.forEach(o => {
                    const tr = document.createElement('tr');
                    const dateStr = o.createdAt ? new Date(o.createdAt).toLocaleString('vi-VN') : '—';
                    tr.innerHTML = `
                        <td><strong>#${o.id}</strong></td>
                        <td>${escapeHtml(o.customerName || 'Khách hàng')}</td>
                        <td><strong class="text-success">${formatVnd(o.totalAmount)}</strong></td>
                        <td><span class="status-chip chip-success">${escapeHtml(o.status)}</span></td>
                        <td><small class="text-muted">${dateStr}</small></td>
                    `;
                    ordBody.append(tr);
                });
            }

        } catch (err) {
            console.error('Error loading branch details:', err);
        }
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
        if (!window.confirm(`Xóa chi nhánh ${branch.name}? Chỉ chi nhánh chưa có dữ liệu nghiệp vụ mới xóa được.`)) return;
        try {
            const response = await fetch(`/api/admin/branches/${branch.id}`, { method: 'DELETE', headers: authHeaders() });
            if (!response.ok) throw new Error(await responseMessage(response, 'Không thể xóa chi nhánh.'));
            showNotice('Đã xóa chi nhánh.');
            await load();
        } catch (error) { showNotice(error.message, true); }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str).replace(/[&<>"']/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]));
    }

    function optionalNumber(id) { const value = document.getElementById(id).value; return value === '' ? null : Number(value); }
    function authHeaders() { const token = localStorage.getItem(tokenKey); return token ? { Authorization: `Bearer ${token}` } : {}; }
    async function responseMessage(response, fallback) { try { return (await response.json()).message || fallback; } catch (_) { return fallback; } }
    function showNotice(message, error = false) { const notice = document.getElementById('branch-notice'); notice.textContent = message; notice.classList.toggle('error', error); notice.hidden = false; }
})();
