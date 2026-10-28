(function () {
    const ACCESS_TOKEN_KEY = 'pawconnect.accessToken';
    let signedIn = false;

    document.addEventListener('DOMContentLoaded', () => {
        document.getElementById('headerSearchBtn')?.addEventListener('click', openSearch);
        document.getElementById('cartBtn')?.addEventListener('click', openCart);
        document.getElementById('notificationBtn')?.addEventListener('click', toggleNotificationDropdown);
        document.getElementById('markAllReadBtn')?.addEventListener('click', markAllNotificationsAsRead);
        document.addEventListener('click', handleOutsideNotificationClick);
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
            document.dispatchEvent(new CustomEvent('pawconnect:authenticated', { detail: user }));
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

    let notificationPollInterval = null;

    function setLoggedOutAccountState() {
        signedIn = false;
        if (notificationPollInterval) {
            clearInterval(notificationPollInterval);
            notificationPollInterval = null;
        }
        closeNotificationDropdown();
        const badge = document.getElementById('notificationBadge');
        if (badge) {
            badge.textContent = '0';
            badge.style.display = 'none';
        }
        document.querySelector('.main-header')?.classList.remove('main-header-signed-in');
        document.getElementById('account-summary')?.setAttribute('hidden', '');
        updateAccountAvatar(null);
        document.querySelectorAll('[data-customer-only], [data-staff-only], [data-admin-only]')
            .forEach((link) => link.hidden = true);
        const notifWrapper = document.getElementById('notificationWrapper');
        if (notifWrapper) notifWrapper.hidden = true;
        const cartButton = document.getElementById('cartBtn');
        if (cartButton) cartButton.hidden = false;
        document.getElementById('management-menu-toggle')?.setAttribute('hidden', '');
        closeManagementMenu();
        document.dispatchEvent(new Event('pawconnect:signed-out'));
    }

    function updateRoleNavigation(role) {
        const isCustomer = role === 'CUSTOMER';
        const isStaff = role === 'ADMIN' || role === 'BRANCH_MANAGER';
        const cartButton = document.getElementById('cartBtn');
        if (cartButton) cartButton.hidden = !isCustomer;
        document.querySelector('.main-header')?.classList.add('main-header-signed-in');
        document.querySelectorAll('[data-customer-only]').forEach((link) => link.hidden = !isCustomer);
        document.querySelectorAll('[data-staff-only]').forEach((link) => link.hidden = !isStaff);
        document.querySelectorAll('[data-admin-only]').forEach((link) => link.hidden = role !== 'ADMIN');
        const menuToggle = document.getElementById('management-menu-toggle');
        if (menuToggle) menuToggle.hidden = !isStaff;
        if (!isStaff) closeManagementMenu();

        // Chuông thông báo hiển thị cho tất cả tài khoản đã đăng nhập (Khách hàng, Quản lý chi nhánh, Quản trị viên)
        const notifWrapper = document.getElementById('notificationWrapper');
        if (notifWrapper) notifWrapper.hidden = false;
        fetchUnreadNotificationCount();
        if (!notificationPollInterval) {
            notificationPollInterval = setInterval(fetchUnreadNotificationCount, 20000);
        }

        if (isCustomer) {
            refreshCartCount();
        }
        }
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
        if (role === 'BRANCH_MANAGER') return 'Nhân viên chi nhánh';
        return 'Khách';
    }

    function accountLabel(user) {
        if (user.role !== 'CUSTOMER') return supportDisplayName(user.fullName) || user.username || roleLabel(user.role);
        const names = (user.fullName || '').trim().split(/\s+/).filter(Boolean);
        return names.at(-1) || user.username || 'Khách';
    }

    function supportDisplayName(name) {
        return ({
            'Quản lý TP.HCM': 'Nhân viên TP.HCM',
            'Quản lý Hà Nội': 'Nhân viên Hà Nội',
            'Quản lý Đà Nẵng': 'Nhân viên Đà Nẵng'
        })[name] || name;
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

    let selectedCartItemIds = new Set();
    const quantityDebounceMap = new Map();

    function renderCartDrawer(cart) {
        closeCartDrawer();
        const items = cart.items || [];
        const currentItemIds = new Set(items.map(it => it.id));
        if (selectedCartItemIds.size === 0) {
            items.forEach(it => selectedCartItemIds.add(it.id));
        } else {
            for (const id of selectedCartItemIds) {
                if (!currentItemIds.has(id)) selectedCartItemIds.delete(id);
            }
            if (selectedCartItemIds.size === 0 && items.length > 0) {
                items.forEach(it => selectedCartItemIds.add(it.id));
            }
        }
        const allSelected = items.length > 0 && items.every(it => selectedCartItemIds.has(it.id));
        const backdrop = document.createElement('div');
        backdrop.className = 'cart-drawer-backdrop';
        backdrop.addEventListener('click', closeCartDrawer);
        const drawer = document.createElement('aside');
        drawer.className = 'cart-drawer';
        drawer.id = 'cartDrawer';
        drawer.innerHTML = `
            <div class="cart-drawer-header">
                <div class="cart-header-title-row">
                    <h2>Giỏ hàng</h2>
                    <button class="cart-drawer-close" type="button" aria-label="Đóng giỏ hàng"><i class="fas fa-times"></i></button>
                </div>
                ${items.length ? `
                <div class="cart-select-all-bar">
                    <label class="cart-checkbox-label">
                        <input type="checkbox" id="cartSelectAll" class="cart-checkbox" ${allSelected ? 'checked' : ''}>
                        <span>Chọn tất cả (${items.length} sản phẩm)</span>
                    </label>
                </div>` : ''}
            </div>
            <div class="cart-drawer-items"></div>
            <div class="cart-drawer-footer">
                <div class="cart-footer-summary">
                    <span>Tổng cộng:</span>
                    <strong id="cartTotalPrice" class="cart-total-amount">0 VND</strong>
                </div>
            </div>
            ${items.length ? '<button type="button" id="cartCheckoutBtn" class="cart-checkout-link btn-pill">Tiến hành mua</button>' : ''}`;

        drawer.querySelector('.cart-drawer-close').addEventListener('click', closeCartDrawer);
        const container = drawer.querySelector('.cart-drawer-items');
        if (!items.length) {
            container.innerHTML = '<p class="cart-empty">Giỏ hàng của bạn đang trống.</p>';
        }

        const selectAllCheckbox = drawer.querySelector('#cartSelectAll');
        if (selectAllCheckbox) {
            selectAllCheckbox.addEventListener('change', (e) => {
                const checked = e.target.checked;
                items.forEach(it => {
                    if (checked) selectedCartItemIds.add(it.id);
                    else selectedCartItemIds.delete(it.id);
                });
                drawer.querySelectorAll('.cart-item-checkbox').forEach(cb => { cb.checked = checked; });
                updateCartTotals();
            });
        }

        items.forEach((item) => {
            const isSelected = selectedCartItemIds.has(item.id);
            const maxStock = item.stock != null ? item.stock : 99;
            const row = document.createElement('article');
            row.className = 'cart-row';
            row.dataset.itemId = String(item.id);
            row.innerHTML = `
                <label class="cart-checkbox-label">
                    <input type="checkbox" class="cart-checkbox cart-item-checkbox" data-id="${item.id}" ${isSelected ? 'checked' : ''}>
                </label>
                <div class="cart-row-main">
                    <p class="cart-row-name"></p>
                    <p class="cart-row-meta">
                        <span class="cart-row-unit-price">${formatMoney(item.price)}</span>
                        ${item.branchName ? `<span class="cart-row-branch">· ${escapeHtml(item.branchName)}</span>` : ''}
                    </p>
                    <div class="cart-row-bottom">
                        <div class="cart-stepper">
                            <button type="button" class="cart-stepper-btn cart-btn-minus" aria-label="Giảm số lượng" ${item.quantity <= 1 ? 'disabled' : ''}>
                                <i class="fas fa-minus"></i>
                            </button>
                            <span class="cart-stepper-qty">${item.quantity}</span>
                            <button type="button" class="cart-stepper-btn cart-btn-plus" aria-label="Tăng số lượng" ${item.quantity >= maxStock ? 'disabled title="Đã đạt số lượng tồn kho tối đa (' + maxStock + ')"' : ''}>
                                <i class="fas fa-plus"></i>
                            </button>
                        </div>
                        <span class="cart-row-subtotal">${formatMoney(Number(item.price || 0) * item.quantity)}</span>
                    </div>
                    ${item.stock != null ? `
                        <small class="cart-stock-hint ${item.quantity >= maxStock ? 'cart-stock-max' : ''}">
                            ${item.quantity >= maxStock ? `Đã đạt tối đa (${maxStock} trong kho)` : `Còn ${maxStock} trong kho`}
                        </small>
                    ` : ''}
                </div>
                <button class="cart-remove" type="button" title="Xóa khỏi giỏ hàng" aria-label="Xóa khỏi giỏ hàng">
                    <i class="fas fa-trash"></i>
                </button>
            `;
            row.querySelector('.cart-row-name').textContent = item.productName;

            row.querySelector('.cart-item-checkbox').addEventListener('change', (e) => {
                if (e.target.checked) selectedCartItemIds.add(item.id);
                else selectedCartItemIds.delete(item.id);
                if (selectAllCheckbox) {
                    selectAllCheckbox.checked = items.every(it => selectedCartItemIds.has(it.id));
                }
                updateCartTotals();
            });

            const btnMinus = row.querySelector('.cart-btn-minus');
            btnMinus.addEventListener('click', () => {
                if (item.quantity <= 1) return;
                const prev = item.quantity;
                item.quantity -= 1;
                updateItemRowUI(item, row, btnMinus, btnPlus, maxStock);
                updateCartTotals();
                updateHeaderCartCount();
                queueQuantitySync(item, item.quantity, prev, row, btnMinus, btnPlus, maxStock);
            });

            const btnPlus = row.querySelector('.cart-btn-plus');
            btnPlus.addEventListener('click', () => {
                if (item.quantity >= maxStock) {
                    window.alert(`Sản phẩm này chỉ còn ${maxStock} cái trong kho, không thể tăng thêm.`);
                    return;
                }
                const prev = item.quantity;
                item.quantity += 1;
                updateItemRowUI(item, row, btnMinus, btnPlus, maxStock);
                updateCartTotals();
                updateHeaderCartCount();
                queueQuantitySync(item, item.quantity, prev, row, btnMinus, btnPlus, maxStock);
            });

            row.querySelector('.cart-remove').addEventListener('click', () => removeItem(item.id));
            container.append(row);
        });

        function updateItemRowUI(item, row, btnMinus, btnPlus, maxStock) {
            const qtyElem = row.querySelector('.cart-stepper-qty');
            if (qtyElem) qtyElem.textContent = String(item.quantity);
            const subtotalElem = row.querySelector('.cart-row-subtotal');
            if (subtotalElem) subtotalElem.textContent = formatMoney(Number(item.price || 0) * item.quantity);
            if (btnMinus) btnMinus.disabled = item.quantity <= 1;
            if (btnPlus) {
                btnPlus.disabled = item.quantity >= maxStock;
                if (item.quantity >= maxStock) {
                    btnPlus.setAttribute('title', `Đã đạt số lượng tồn kho tối đa (${maxStock})`);
                } else {
                    btnPlus.removeAttribute('title');
                }
            }
            const hintElem = row.querySelector('.cart-stock-hint');
            if (hintElem && item.stock != null) {
                hintElem.className = 'cart-stock-hint ' + (item.quantity >= maxStock ? 'cart-stock-max' : '');
                hintElem.textContent = item.quantity >= maxStock
                    ? `Đã đạt tối đa (${maxStock} trong kho)`
                    : `Còn ${maxStock} trong kho`;
            }
        }

        function updateHeaderCartCount() {
            const totalCount = items.reduce((sum, it) => sum + it.quantity, 0);
            const badge = document.getElementById('cartCount');
            if (badge) badge.textContent = String(totalCount);
        }

        function queueQuantitySync(item, targetQty, stepPrevQty, row, btnMinus, btnPlus, maxStock) {
            const existing = quantityDebounceMap.get(item.id);
            const originalPrevQty = existing ? existing.prevQty : stepPrevQty;
            if (existing) {
                clearTimeout(existing.timer);
            }
            const timer = setTimeout(async () => {
                quantityDebounceMap.delete(item.id);
                try {
                    const response = await fetch(`/api/cart/items/${item.id}?quantity=${targetQty}`, {
                        method: 'PUT',
                        headers: authHeaders()
                    });
                    if (!response.ok) {
                        const err = await responseMessage(response, 'Không thể cập nhật số lượng.');
                        window.alert(err);
                        item.quantity = originalPrevQty;
                        if (document.getElementById('cartDrawer')) {
                            updateItemRowUI(item, row, btnMinus, btnPlus, maxStock);
                            updateCartTotals();
                        }
                        await refreshCartCount();
                    }
                } catch (e) {
                    window.alert('Lỗi kết nối khi cập nhật số lượng.');
                    item.quantity = originalPrevQty;
                    if (document.getElementById('cartDrawer')) {
                        updateItemRowUI(item, row, btnMinus, btnPlus, maxStock);
                        updateCartTotals();
                    }
                    await refreshCartCount();
                }
            }, 300);
            quantityDebounceMap.set(item.id, { timer, targetQty, prevQty: originalPrevQty });
        }

        function updateCartTotals() {
            const selectedItems = items.filter(it => selectedCartItemIds.has(it.id));
            const total = selectedItems.reduce((sum, it) => sum + Number(it.price || 0) * it.quantity, 0);
            const totalElem = drawer.querySelector('#cartTotalPrice');
            if (totalElem) totalElem.textContent = formatMoney(total);
            const checkoutBtn = drawer.querySelector('#cartCheckoutBtn');
            if (checkoutBtn) {
                checkoutBtn.disabled = selectedItems.length === 0;
                checkoutBtn.textContent = selectedItems.length > 0
                    ? `Tiến hành mua (${selectedItems.length})`
                    : 'Tiến hành mua (0)';
            }
        }

        const checkoutBtn = drawer.querySelector('#cartCheckoutBtn');
        if (checkoutBtn) {
            checkoutBtn.addEventListener('click', async () => {
                if (quantityDebounceMap.size > 0) {
                    checkoutBtn.disabled = true;
                    checkoutBtn.textContent = 'Đang lưu giỏ hàng...';
                    const flushPromises = [];
                    for (const [id, entry] of quantityDebounceMap.entries()) {
                        clearTimeout(entry.timer);
                        flushPromises.push(
                            fetch(`/api/cart/items/${id}?quantity=${entry.targetQty}`, {
                                method: 'PUT',
                                headers: authHeaders()
                            })
                        );
                    }
                    quantityDebounceMap.clear();
                    try {
                        await Promise.all(flushPromises);
                    } catch (_) {}
                }

                const selectedItems = items.filter(it => selectedCartItemIds.has(it.id));
                if (selectedItems.length === 0) {
                    window.alert('Vui lòng chọn ít nhất một sản phẩm để thanh toán.');
                    updateCartTotals();
                    return;
                }
                const branches = new Set(selectedItems.map(it => it.branchId));
                if (branches.size > 1) {
                    window.alert('Mỗi đơn chỉ gồm sản phẩm của một chi nhánh. Vui lòng chỉ chọn các sản phẩm cùng chi nhánh để thanh toán.');
                    updateCartTotals();
                    return;
                }
                sessionStorage.setItem('pawconnect.selectedCartItemIds', JSON.stringify(selectedItems.map(it => it.id)));
                window.location.assign('/checkout');
            });
        }

        updateCartTotals();
        document.body.append(backdrop, drawer);
    }

    async function changeItemQuantity(itemId, newQty) {
        if (newQty < 1) return;
        try {
            const response = await fetch(`/api/cart/items/${itemId}?quantity=${newQty}`, {
                method: 'PUT',
                headers: authHeaders()
            });
            if (!response.ok) {
                const err = await responseMessage(response, 'Không thể cập nhật số lượng.');
                window.alert(err);
                openCart();
                return;
            }
            await refreshCartCount();
            openCart();
        } catch (e) {
            window.alert('Lỗi kết nối khi cập nhật số lượng.');
            openCart();
        }
    }

    async function removeItem(itemId) {
        if (quantityDebounceMap.has(itemId)) {
            clearTimeout(quantityDebounceMap.get(itemId).timer);
            quantityDebounceMap.delete(itemId);
        }
        const response = await fetch(`/api/cart/items/${itemId}`, { method: 'DELETE', headers: authHeaders() });
        if (!response.ok) {
            window.alert('Không thể xóa sản phẩm khỏi giỏ hàng.');
            return;
        }
        selectedCartItemIds.delete(itemId);
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

    function escapeHtml(value) {
        if (!value) return '';
        const div = document.createElement('div');
        div.textContent = value;
        return div.innerHTML;
    }

    function handleOutsideNotificationClick(event) {
        const wrapper = document.querySelector('.notification-wrapper');
        if (wrapper && !wrapper.contains(event.target)) {
            closeNotificationDropdown();
        }
    }

    function toggleNotificationDropdown(event) {
        event.stopPropagation();
        const dropdown = document.getElementById('notificationDropdown');
        const btn = document.getElementById('notificationBtn');
        if (!dropdown || !btn) return;
        const wrapper = btn.closest('.notification-wrapper');
        if (wrapper) {
            wrapper.style.position = 'relative';
            wrapper.style.display = 'inline-flex';
            wrapper.style.alignItems = 'center';
        }
        const isHidden = dropdown.hidden || dropdown.style.display === 'none';
        if (isHidden) {
            dropdown.hidden = false;
            dropdown.style.cssText = 'position: absolute !important; top: calc(100% + 10px) !important; right: 0 !important; width: 380px !important; max-width: min(380px, 92vw) !important; background: #ffffff !important; border: 1px solid #e2e8f0 !important; border-radius: 16px !important; box-shadow: 0 16px 45px rgba(0, 0, 0, 0.18) !important; z-index: 999999 !important; overflow-x: hidden !important; overflow-y: hidden !important; text-align: left !important; display: block !important; box-sizing: border-box !important; white-space: normal !important;';
            btn.setAttribute('aria-expanded', 'true');
            loadNotifications();
        } else {
            closeNotificationDropdown();
        }
    }

    function closeNotificationDropdown() {
        const dropdown = document.getElementById('notificationDropdown');
        const btn = document.getElementById('notificationBtn');
        if (dropdown) {
            dropdown.hidden = true;
            dropdown.style.display = 'none';
        }
        if (btn) btn.setAttribute('aria-expanded', 'false');
    }

    async function fetchUnreadNotificationCount() {
        const token = localStorage.getItem(ACCESS_TOKEN_KEY);
        if (!token) return;
        try {
            const response = await fetch('/api/notifications/unread-count', { headers: authHeaders() });
            if (!response.ok) return;
            const data = await response.json();
            const badge = document.getElementById('notificationBadge');
            if (badge) {
                const count = Number(data.count != null ? data.count : (data.unreadCount || 0));
                if (count > 0) {
                    badge.textContent = count > 99 ? '99+' : String(count);
                    badge.style.display = 'inline-flex';
                } else {
                    badge.textContent = '0';
                    badge.style.display = 'none';
                }
            }
        } catch (_) {
            // ignore network glitch
        }
    }

    async function loadNotifications() {
        const listContainer = document.getElementById('notificationList');
        if (!listContainer) return;
        listContainer.style.overflowX = "hidden"; listContainer.style.whiteSpace = "normal"; listContainer.innerHTML = '<div class="notification-loading" style="padding: 1rem; text-align: center; color: var(--text-muted);"><i class="fas fa-spinner fa-spin"></i> Đang tải thông báo...</div>';
        try {
            const response = await fetch('/api/notifications', { headers: authHeaders() });
            if (!response.ok) {
                listContainer.innerHTML = '<div class="notification-empty" style="padding: 1rem; text-align: center; color: var(--text-muted);">Không thể tải thông báo.</div>';
                return;
            }
            const notifications = await response.json();
            renderNotifications(notifications);
        } catch (_) {
            listContainer.innerHTML = '<div class="notification-empty" style="padding: 1rem; text-align: center; color: var(--text-muted);">Lỗi kết nối khi tải thông báo.</div>';
        }
    }

    function renderNotifications(notifications) {
        const listContainer = document.getElementById('notificationList');
        if (!listContainer) return;
        listContainer.replaceChildren();
        if (!notifications || notifications.length === 0) {
            listContainer.innerHTML = `
                <div class="notification-empty" style="padding: 2.5rem 1.5rem !important; text-align: center !important; color: #718096 !important;">
                    <i class="far fa-bell" style="font-size: 2rem !important; margin-bottom: 8px !important; opacity: 0.4 !important; display: block !important;"></i>
                    <p style="margin: 0 !important; font-size: 0.9rem !important;">Bạn chưa có thông báo nào</p>
                </div>`;
            return;
        }

        notifications.forEach((item) => {
            const el = document.createElement('div');
            el.className = `notification-item ${item.read ? 'read' : 'unread'}`;
            el.style.cssText = 'display: flex !important; align-items: flex-start !important; gap: 0.85rem !important; padding: 0.85rem 1.15rem !important; border-bottom: 1px solid #f1f5f9 !important; cursor: pointer !important; position: relative !important; text-align: left !important; background: ' + (item.read ? '#ffffff' : '#fff8f5') + ' !important; transition: background 0.15s ease !important; width: 100% !important; white-space: normal !important; box-sizing: border-box !important;';

            let iconHtml = '<i class="fas fa-bell"></i>';
            let iconBg = 'rgba(226, 123, 88, 0.12)';
            let iconColor = '#e27b58';
            if (item.type === 'ADOPTION_APPROVED') {
                iconHtml = '<i class="fas fa-check"></i>';
                iconBg = 'rgba(16, 185, 129, 0.12)';
                iconColor = '#10b981';
            } else if (item.type === 'ADOPTION_REJECTED') {
                iconHtml = '<i class="fas fa-times"></i>';
                iconBg = 'rgba(239, 68, 68, 0.12)';
                iconColor = '#ef4444';
            } else if (item.type === 'ORDER_CREATED') {
                iconHtml = '<i class="fas fa-bag-shopping"></i>';
                iconBg = 'rgba(226, 123, 88, 0.15)';
                iconColor = '#e27b58';
            } else if (item.type === 'ADOPTION_APPLIED') {
                iconHtml = '<i class="fas fa-paw"></i>';
                iconBg = 'rgba(99, 102, 241, 0.12)';
                iconColor = '#6366f1';
            } else if (item.type === 'SERVICE_BOOKING_CREATED' || item.type === 'SERVICE_BOOKING_CONFIRMED') {
                iconHtml = '<i class="fas fa-calendar-check"></i>';
                iconBg = 'rgba(16, 185, 129, 0.12)';
                iconColor = '#10b981';
            } else if (item.type === 'SERVICE_BOOKING_CANCELLED') {
                iconHtml = '<i class="fas fa-calendar-xmark"></i>';
                iconBg = 'rgba(239, 68, 68, 0.12)';
                iconColor = '#ef4444';
            }

            const timeStr = item.createdAt ? formatRelativeTime(item.createdAt) : '';

            el.innerHTML = `
                <div class="notification-item-icon" style="flex: 0 0 34px !important; width: 34px !important; height: 34px !important; border-radius: 50% !important; display: inline-flex !important; align-items: center !important; justify-content: center !important; font-size: 0.95rem !important; margin-top: 2px !important; background: ${iconBg} !important; color: ${iconColor} !important;">
                    ${iconHtml}
                </div>
                <div class="notification-item-content" style="flex: 1 1 0% !important; min-width: 0 !important; max-width: 100% !important; text-align: left !important; white-space: normal !important; overflow-wrap: break-word !important; word-break: break-word !important;">
                    <h4 class="notification-item-title" style="font-size: 0.88rem !important; font-weight: 700 !important; color: #2d3748 !important; margin: 0 0 0.25rem 0 !important; line-height: 1.35 !important; white-space: normal !important; overflow-wrap: break-word !important; word-break: break-word !important;">${escapeHtml(item.title)}</h4>
                    <p class="notification-item-desc" style="font-size: 0.82rem !important; color: #64748b !important; margin: 0 0 0.35rem 0 !important; line-height: 1.45 !important; white-space: normal !important; overflow-wrap: break-word !important; word-break: break-word !important;">${escapeHtml(item.content)}</p>
                    <span class="notification-item-time" style="font-size: 0.75rem !important; color: #94a3b8 !important; font-weight: 600 !important; display: block !important; white-space: normal !important;">${timeStr}</span>
                </div>
                ${!item.read ? '<span class="notification-unread-dot" style="width: 8px !important; height: 8px !important; border-radius: 50% !important; background: #e53e3e !important; flex-shrink: 0 !important; margin-top: 6px !important;" title="Chưa đọc"></span>' : ''}
            `;

            el.addEventListener('click', async () => {
                if (!item.read) {
                    try {
                        await fetch(`/api/notifications/${item.id}/read`, { method: 'PUT', headers: authHeaders() });
                        item.read = true;
                        el.classList.remove('unread');
                        el.classList.add('read');
                        el.style.background = '#ffffff';
                        el.querySelector('.notification-unread-dot')?.remove();
                        fetchUnreadNotificationCount();
                    } catch (_) {}
                }
                closeNotificationDropdown();
                if (item.link) {
                    window.location.assign(item.link);
                }
            });

            listContainer.appendChild(el);
        });
    }

    async function markAllNotificationsAsRead() {
        try {
            await fetch('/api/notifications/read-all', { method: 'PUT', headers: authHeaders() });
            const badge = document.getElementById('notificationBadge');
            if (badge) {
                badge.textContent = '0';
                badge.style.display = 'none';
            }
            loadNotifications();
        } catch (_) {}
    }

    function formatRelativeTime(dateStr) {
        try {
            const date = new Date(dateStr);
            const now = new Date();
            const diffMs = now - date;
            const diffMin = Math.floor(diffMs / 60000);
            if (diffMin < 1) return 'Vừa xong';
            if (diffMin < 60) return `${diffMin} phút trước`;
            const diffHour = Math.floor(diffMin / 60);
            if (diffHour < 24) return `${diffHour} giờ trước`;
            const diffDay = Math.floor(diffHour / 24);
            if (diffDay < 7) return `${diffDay} ngày trước`;
            return date.toLocaleDateString('vi-VN');
        } catch (_) {
            return '';
        }
    }

    window.PawConnectCart = { add, open: openCart, refreshCount: refreshCartCount };
})();
