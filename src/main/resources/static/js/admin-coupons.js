(() => {
    const token = localStorage.getItem('pawconnect.accessToken');
    const headers = () => ({ Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' });
    document.addEventListener('DOMContentLoaded', async () => {
        try {
            const me = await fetch('/api/auth/me', { headers: headers() });
            if (!me.ok || (await me.json()).role !== 'ADMIN') throw new Error('Chỉ quản trị viên được quản lý mã giảm giá.');
            document.getElementById('coupons-admin').hidden = false;
            document.getElementById('coupon-form').addEventListener('submit', create);
            document.getElementById('coupon-type').addEventListener('change', () => {
                document.getElementById('coupon-value').max = document.getElementById('coupon-type').value === 'PERCENT' ? '100' : '1000000000';
            });
            await load();
        } catch (error) { notice(error.message, true); }
    });
    async function load() {
        const response = await fetch('/api/admin/coupons', { headers: headers() });
        if (!response.ok) throw new Error('Không thể tải danh sách mã.');
        const list = document.getElementById('admin-coupon-list'); list.replaceChildren();
        for (const c of await response.json()) {
            const row = document.createElement('article'); row.className = 'coupon-admin-item';
            const body = document.createElement('div');
            const title = document.createElement('strong'); title.textContent = `${c.code} · ${c.name}`;
            const detail = document.createElement('p'); detail.className = 'small mb-0';
            detail.textContent = `${c.discountType === 'PERCENT' ? `Giảm ${c.discountValue}%` : `Giảm ${new Intl.NumberFormat('vi-VN').format(c.discountValue)} VND`} · ${c.usesPerCustomer} lượt/khách · ${c.active ? 'Đang áp dụng' : 'Tạm ngưng'}`;
            const rule = document.createElement('p'); rule.className = 'small mb-0';
            rule.textContent = `${({ ORDER_MINIMUM: 'Theo giá trị đơn', FIRST_ORDER: 'Chưa có đơn mua hàng chưa hủy', COMPLETED_SPA: 'Có lịch spa đã hoàn tất' })[c.condition]}${Number(c.minimumSubtotal) > 0 ? ` · Tiền hàng > ${new Intl.NumberFormat('vi-VN').format(c.minimumSubtotal)} VND` : ''}`;
            body.append(title, detail, rule);
            const toggle = document.createElement('button'); toggle.className = 'btn btn-outline-secondary'; toggle.type = 'button';
            toggle.textContent = c.active ? 'Tạm ngưng' : 'Kích hoạt';
            toggle.addEventListener('click', async () => {
                toggle.disabled = true;
                try {
                    const result = await fetch(`/api/admin/coupons/${c.id}/active`, { method: 'PATCH', headers: headers(), body: JSON.stringify({ active: !c.active }) });
                    if (!result.ok) throw new Error(await message(result));
                    await load();
                } catch (error) { notice(error.message, true); toggle.disabled = false; }
            });
            row.append(body, toggle); list.append(row);
        }
    }
    async function create(event) {
        event.preventDefault();
        const form = event.target; const button = document.getElementById('create-coupon'); button.disabled = true;
        try {
            const data = Object.fromEntries(new FormData(form));
            for (const key of ['discountValue', 'minimumSubtotal', 'usesPerCustomer']) data[key] = Number(data[key]);
            const response = await fetch('/api/admin/coupons', { method: 'POST', headers: headers(), body: JSON.stringify(data) });
            if (!response.ok) throw new Error(await message(response));
            form.reset(); document.getElementById('coupon-value').max = '100';
            notice('Đã thêm mã giảm giá.'); await load();
        } catch (error) { notice(error.message, true); }
        finally { button.disabled = false; }
    }
    async function message(response) { try { const data = await response.json(); return data.message || Object.values(data.errors || {})[0] || 'Dữ liệu mã chưa hợp lệ.'; } catch (_) { return 'Không thể lưu mã giảm giá.'; } }
    function notice(text, error = false) { const e = document.getElementById('coupons-notice'); e.textContent = text; e.hidden = false; e.classList.toggle('error', error); }
})();
