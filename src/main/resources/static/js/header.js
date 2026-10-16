(function () {
    const ACCESS_TOKEN_KEY = 'pawconnect.accessToken';
    let signedIn = false;

    document.addEventListener('DOMContentLoaded', () => {
        document.getElementById('headerSearchBtn')?.addEventListener('click', openSearch);
        document.getElementById('cartBtn')?.addEventListener('click', openCart);
        document.getElementById('auth-action')?.addEventListener('click', handleAuthAction);
        document.getElementById('management-menu-toggle')?.addEventListener('click', openManagementMenu);
        document.getElementById('management-menu-close')?.addEventListener('click', closeManagementMenu);
        document.getElementById('management-sidebar-backdrop')?.addEventListener('click', closeManagementMenu);
        document.querySelectorAll('.management-sidebar-links a').forEach((link) => link.addEventListener('click', closeManagementMenu));
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') closeManagementMenu();
        });
        document.querySelectorAll('[data-booking-access]').forEach((link) => {
            link.addEventListener('click', requireLoginForBooking);
        });
        document.addEventListener('pawconnect:profile-updated', loadAccountStatus);
        setLoggedOutAccountState();
        loadAccountStatus();
        refreshCartCount();
    });

    function authHeaders() {
        const token = localStorage.getItem(ACCESS_TOKEN_KEY);
        return token ? { Authorization: `Bearer ${token}` } : {};
    }

    async function loadAccountStatus() {
        const token = localStorage.getItem(ACCESS_TOKEN_KEY);
        if (!token) return;
        try {
            const response = await fetch('/api/auth/me', { headers: authHeaders() });
            if (!response.ok) {
                if (response.status === 401) clearStoredAuth();
                return;
            }
            const user = await response.json();
            signedIn = true;
            const status = document.getElementById('account-status');
            if (status) status.textContent = accountLabel(user);
            const summary = document.getElementById('account-summary');
            if (summary) {
                summary.hidden = false;
                summary.title = `Mở hồ sơ của ${user.fullName || user.username || roleLabel(user.role)}`;
            }
            updateAccountAvatar(user.avatarUrl);
            updateRoleNavigation(user.role);
            const action = document.getElementById('auth-action');
            if (action) {
                action.textContent = 'Đăng xuất';
            }
        } catch (_) {
            // A stale token or a temporary network fault must not break page initialization.
        }
    }

    function handleAuthAction(event) {
        if (!signedIn) {
            window.location.assign(`/auth/login?returnTo=${encodeURIComponent(currentPath())}`);
            return;
        }
        clearStoredAuth();
        window.location.assign('/shop');
    }

    function requireLoginForBooking(event) {
        if (localStorage.getItem(ACCESS_TOKEN_KEY)) return;
        event.preventDefault();
        window.location.assign('/auth/login?returnTo=%2Fservice');
    }

    function setLoggedOutAccountState() {
        signedIn = false;
        document.querySelector('.main-header')?.classList.remove('main-header-signed-in');
        document.getElementById('account-summary')?.setAttribute('hidden', '');
        updateAccountAvatar(null);
        document.querySelectorAll('[data-customer-only], [data-staff-only], [data-admin-only]')
            .forEach((link) => link.hidden = true);
        document.getElementById('management-menu-toggle')?.setAttribute('hidden', '');
        closeManagementMenu();
    }

    function updateRoleNavigation(role) {
        const isCustomer = role === 'CUSTOMER';
        const isStaff = role === 'ADMIN' || role === 'BRANCH_MANAGER';
        document.querySelector('.main-header')?.classList.add('main-header-signed-in');
        document.querySelectorAll('[data-customer-only]').forEach((link) => link.hidden = !isCustomer);
        document.querySelectorAll('[data-staff-only]').forEach((link) => link.hidden = !isStaff);
        document.querySelectorAll('[data-admin-only]').forEach((link) => link.hidden = role !== 'ADMIN');
        const menuToggle = document.getElementById('management-menu-toggle');
        if (menuToggle) menuToggle.hidden = !isStaff;
        if (!isStaff) closeManagementMenu();
    }

    function openManagementMenu() {
        const sidebar = document.getElementById('management-sidebar');
        const backdrop = document.getElementById('management-sidebar-backdrop');
        const toggle = document.getElementById('management-menu-toggle');
        if (!sidebar || !backdrop || !toggle || toggle.hidden) return;
        sidebar.hidden = false;
        backdrop.hidden = false;
        toggle.setAttribute('aria-expanded', 'true');
        document.body.classList.add('management-menu-open');
    }

    function closeManagementMenu() {
        const sidebar = document.getElementById('management-sidebar');
        const backdrop = document.getElementById('management-sidebar-backdrop');
        const toggle = document.getElementById('management-menu-toggle');
        if (sidebar) sidebar.hidden = true;
        if (backdrop) backdrop.hidden = true;
        if (toggle) toggle.setAttribute('aria-expanded', 'false');
        document.body.classList.remove('management-menu-open');
    }

    function clearStoredAuth() {
        signedIn = false;
        ['pawconnect.accessToken', 'pawconnect.refreshToken', 'accessToken', 'refreshToken', 'token']
            .forEach((key) => {
                localStorage.removeItem(key);
                sessionStorage.removeItem(key);
            });
    }

    function currentPath() {
        return `${window.location.pathname}${window.location.search}${window.location.hash}`;
    }

    function roleLabel(role) {
        if (role === 'ADMIN') return 'Quản trị viên';
        if (role === 'BRANCH_MANAGER') return 'Quản lý chi nhánh';
        return 'Khách';
    }

    function accountLabel(user) {
        if (user.role !== 'CUSTOMER') return roleLabel(user.role);
        const names = (user.fullName || '').trim().split(/\s+/).filter(Boolean);
        return names.at(-1) || user.username || 'Khách';
    }

    function updateAccountAvatar(avatarUrl) {
        const avatar = document.getElementById('account-avatar');
        const icon = document.getElementById('account-icon');
        if (!avatar || !icon) return;
        avatar.hidden = !avatarUrl;
        icon.hidden = Boolean(avatarUrl);
        if (avatarUrl) avatar.src = avatarUrl;
        else avatar.removeAttribute('src');
    }

    function openSearch() {
        const adoptionInput = document.getElementById('adoption-search');
        if (adoptionInput) {
            adoptionInput.scrollIntoView({ behavior: 'smooth', block: 'center' });
            adoptionInput.focus();
            return;
        }
        const shopInput = document.getElementById('shopSearch');
        if (shopInput) {
            shopInput.scrollIntoView({ behavior: 'smooth', block: 'center' });
            shopInput.focus();
            return;
        }
        const query = window.prompt('Tìm kiếm sản phẩm hoặc chó con');
        if (query?.trim()) window.location.assign(`/shop?search=${encodeURIComponent(query.trim())}#shop-section`);
    }

    async function add(itemType, id) {
        const token = localStorage.getItem(ACCESS_TOKEN_KEY);
        if (!token) {
            window.alert('Vui lòng đăng nhập bằng tài khoản khách hàng trước khi thêm vào giỏ hàng.');
            return false;
        }

        const itemKey = itemType === 'puppy' ? 'puppyListingId' : 'productId';
        const response = await fetch(`/api/cart/items?quantity=1&${itemKey}=${encodeURIComponent(id)}`, {
            method: 'POST', headers: authHeaders()
        });
        if (response.ok) {
            await refreshCartCount();
            return true;
        }
        if (response.status === 401) window.alert('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
        else if (response.status === 403) window.alert('Chỉ tài khoản khách hàng mới có thể sử dụng giỏ hàng.');
        else window.alert(await responseMessage(response, 'Không thể thêm sản phẩm vì số lượng không đủ hoặc dữ liệu không hợp lệ.'));
        return false;
    }

    async function openCart() {
        const token = localStorage.getItem(ACCESS_TOKEN_KEY);
        if (!token) {
            window.alert('Vui lòng đăng nhập bằng tài khoản khách hàng để xem giỏ hàng.');
            return;
        }
        const response = await fetch('/api/cart', { headers: authHeaders() });
        if (!response.ok) {
            window.alert(response.status === 403
                ? 'Chỉ tài khoản khách hàng mới có thể sử dụng giỏ hàng.'
                : 'Không thể tải giỏ hàng. Vui lòng đăng nhập lại.');
            return;
        }
        const cart = await response.json();
        renderCartDrawer(cart);
    }

    async function refreshCartCount() {
        const token = localStorage.getItem(ACCESS_TOKEN_KEY);
        if (!token) return;
        const response = await fetch('/api/cart', { headers: authHeaders() });
        if (!response.ok) return;
        const cart = await response.json();
        const count = (cart.items || []).reduce((total, item) => total + item.quantity, 0);
        const badge = document.getElementById('cartCount');
        if (badge) badge.textContent = String(count);
    }

    function renderCartDrawer(cart) {
        closeCartDrawer();
        const items = cart.items || [];
        const total = items.reduce((sum, item) => sum + Number(item.price || 0) * item.quantity, 0);
        const backdrop = document.createElement('div');
        backdrop.className = 'cart-drawer-backdrop';
        backdrop.addEventListener('click', closeCartDrawer);
        const drawer = document.createElement('aside');
        drawer.className = 'cart-drawer';
        drawer.id = 'cartDrawer';
        drawer.innerHTML = `
            <div class="cart-drawer-header"><h2>Giỏ hàng</h2><button class="cart-drawer-close" type="button" aria-label="Đóng giỏ hàng"><i class="fas fa-times"></i></button></div>
            <div class="cart-drawer-items"></div>
            <div class="cart-drawer-footer"><span>Tổng cộng</span><strong>${formatMoney(total)}</strong></div>`;
        drawer.querySelector('.cart-drawer-close').addEventListener('click', closeCartDrawer);
        const container = drawer.querySelector('.cart-drawer-items');
        if (!items.length) container.innerHTML = '<p class="cart-empty">Giỏ hàng của bạn đang trống.</p>';
        items.forEach((item) => {
            const row = document.createElement('article');
            row.className = 'cart-row';
            row.innerHTML = `<div><p class="cart-row-name"></p><p class="cart-row-meta">SL: ${item.quantity} · ${formatMoney(Number(item.price || 0) * item.quantity)}</p></div><button class="cart-remove" type="button" title="Xóa khỏi giỏ hàng"><i class="fas fa-trash"></i></button>`;
            row.querySelector('.cart-row-name').textContent = item.productName;
            row.querySelector('.cart-remove').addEventListener('click', () => removeItem(item.id));
            container.append(row);
        });
        document.body.append(backdrop, drawer);
    }

    async function removeItem(itemId) {
        const response = await fetch(`/api/cart/items/${itemId}`, { method: 'DELETE', headers: authHeaders() });
        if (!response.ok) {
            window.alert('Không thể xóa sản phẩm khỏi giỏ hàng.');
            return;
        }
        await refreshCartCount();
        closeCartDrawer();
        openCart();
    }

    function closeCartDrawer() {
        document.getElementById('cartDrawer')?.remove();
        document.querySelector('.cart-drawer-backdrop')?.remove();
    }

    async function responseMessage(response, fallback) {
        try {
            return (await response.json()).message || fallback;
        } catch (_) {
            return fallback;
        }
    }

    function formatMoney(value) {
        return `${new Intl.NumberFormat('vi-VN').format(value)} VND`;
    }

    window.PawConnectCart = { add, open: openCart, refreshCount: refreshCartCount };
})();
