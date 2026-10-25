// --- SHOP PAGE LOGIC ---
let productRequestController = null;
let productLoadSequence = 0;
const branchesMap = new Map();

function getBranchName(branchId) {
    if (!branchId) return 'Chưa cập nhật';
    return branchesMap.get(Number(branchId)) || `Chi nhánh #${branchId}`;
}

document.addEventListener('DOMContentLoaded', async () => {
    // Determine which page we are on
    if (document.getElementById('productGrid')) {
        await loadBranches('branchFilter');
        const query = new URLSearchParams(window.location.search).get('search');
        if (query) document.getElementById('shopSearch').value = query;
        await loadProducts();
        await openLinkedItemModal();
        document.getElementById('applyShopFilters').addEventListener('click', loadProducts);
        document.getElementById('shopSearch').addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                loadProducts();
            }
        });
    }
    
    if (document.getElementById('branchSelect')) {
        const bookingUser = await requireBookingLogin();
        if (!bookingUser) return;
        if (bookingUser.role !== 'CUSTOMER') {
            showBookingAccessMessage('Đặt lịch dịch vụ chỉ dành cho tài khoản khách hàng.');
            return;
        }
        await loadBranches('branchSelect');
        await loadServices();
    }
});

async function loadBranches(elementId) {
    try {
        const res = await fetch('/api/branches');
        const branches = await res.json();
        branches.forEach(b => {
            branchesMap.set(Number(b.id), b.name);
        });
        const select = document.getElementById(elementId);
        branches.forEach(b => {
            const opt = document.createElement('option');
            opt.value = b.id;
            opt.textContent = b.name;
            select.appendChild(opt);
        });
    } catch (e) {
        console.error("Failed to load branches");
    }
}

async function loadProducts() {
    const branchId = document.getElementById('branchFilter').value;
    const type = document.getElementById('typeFilter').value;
    const query = document.getElementById('shopSearch').value.trim().toLocaleLowerCase('vi');
    const requestSequence = ++productLoadSequence;
    const searchButton = document.getElementById('applyShopFilters');

    productRequestController?.abort();
    productRequestController = new AbortController();
    const { signal } = productRequestController;
    if (searchButton) searchButton.disabled = true;

    let products = [];

    try {
        if (!type || type === 'product') {
            let url = '/api/products?';
            if (branchId) url += `branchId=${branchId}`;
            const res = await fetch(url, { signal });
            let json = await res.json();
            json.forEach(p => p.itemType = 'product');
            products = products.concat(json);
        }
        if (!type || type === 'puppy') {
            let url = '/api/puppy-listings?';
            if (branchId) url += `branchId=${branchId}`;
            const res = await fetch(url, { signal });
            let json = await res.json();
            json.forEach(p => p.itemType = 'puppy');
            products = products.concat(json);
        }
        const matches = products.filter((product) => {
            if (!query) return true;
            const searchable = product.itemType === 'puppy'
                ? [product.listingTitle, product.breedCode, product.description]
                : [product.name, product.description, product.ingredients, product.targetAudience];
            return searchable.filter(Boolean).join(' ').toLocaleLowerCase('vi').includes(query);
        });
        if (requestSequence !== productLoadSequence) return;
        currentFilteredProducts = matches;
        currentShopPage = 1;
        renderShopPage();
    } catch (e) {
        if (e.name === 'AbortError') return;
        if (requestSequence !== productLoadSequence) return;
        document.getElementById('productGrid').innerHTML = '<p>Không thể tải sản phẩm. Vui lòng thử lại.</p>';
    } finally {
        if (requestSequence === productLoadSequence && searchButton) {
            searchButton.disabled = false;
        }
    }
}

let currentFilteredProducts = [];
let currentShopPage = 1;
const SHOP_PAGE_SIZE = 6;

function renderShopPage() {
    const totalItems = currentFilteredProducts.length;
    const totalPages = Math.ceil(totalItems / SHOP_PAGE_SIZE) || 1;
    if (currentShopPage > totalPages) currentShopPage = totalPages;
    if (currentShopPage < 1) currentShopPage = 1;

    const start = (currentShopPage - 1) * SHOP_PAGE_SIZE;
    const pageItems = currentFilteredProducts.slice(start, start + SHOP_PAGE_SIZE);

    const type = document.getElementById('typeFilter').value;
    const query = document.getElementById('shopSearch').value.trim().toLocaleLowerCase('vi');
    renderProducts(pageItems, type, query, totalItems);
    renderShopPagination(totalPages);
}

function renderShopPagination(totalPages) {
    const container = document.getElementById('shopPagination');
    if (!container) return;
    if (totalPages <= 1) {
        container.innerHTML = '';
        return;
    }

    let html = `
        <button class="paw-pagination-btn" ${currentShopPage <= 1 ? 'disabled' : ''} onclick="changeShopPage(${currentShopPage - 1})">
            <i class="fas fa-chevron-left"></i> Trang trước
        </button>
    `;

    for (let p = 1; p <= totalPages; p++) {
        html += `
            <button class="paw-pagination-btn ${p === currentShopPage ? 'active' : ''}" onclick="changeShopPage(${p})">
                ${p}
            </button>
        `;
    }

    html += `
        <button class="paw-pagination-btn" ${currentShopPage >= totalPages ? 'disabled' : ''} onclick="changeShopPage(${currentShopPage + 1})">
            Trang sau <i class="fas fa-chevron-right"></i>
        </button>
    `;
    container.innerHTML = html;
}

function changeShopPage(page) {
    currentShopPage = page;
    renderShopPage();
    document.getElementById('shop-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function renderProducts(products, type, query, totalItems = null) {
    const grid = document.getElementById('productGrid');
    grid.innerHTML = '';
    
    const countToCheck = totalItems !== null ? totalItems : products.length;
    if(countToCheck === 0) {
        const emptyMessage = query
            ? `Không tìm thấy sản phẩm phù hợp với “${escapeHtml(query)}”.`
            : type === 'puppy'
                ? 'Hiện chưa có chó con đang được rao bán.'
                : type === 'product'
                    ? 'Hiện chưa có sản phẩm thuộc lựa chọn này.'
                    : 'Hiện chưa có sản phẩm để hiển thị.';
        grid.innerHTML = `<p>${emptyMessage}</p>`;
        return;
    }

    products.forEach(p => {
        const card = document.createElement('div');
        card.className = 'product-card';
        if (p.itemType === 'puppy') card.innerHTML += `<div class="product-badge">Pet</div>`;
        
        const safeProductJson = JSON.stringify(p).replace(/"/g, '&quot;');
        const title = p.itemType === 'puppy' ? p.listingTitle : p.name;
        const price = p.itemType === 'puppy' ? p.pricePerPuppyVnd : p.price;
        const branchName = getBranchName(p.branchId);
        
        card.innerHTML += `
            <img src="${p.imageUrl || 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?q=80&w=800&auto=format&fit=crop'}" alt="product" class="product-image" onclick="openModal(${safeProductJson})" style="cursor:pointer">
            <h3 class="product-title" onclick="openModal(${safeProductJson})" style="cursor:pointer"></h3>
            <div class="product-branch"><i class="fas fa-location-dot"></i> <span>${escapeHtml(branchName)}</span></div>
            <p class="product-price">${price.toLocaleString()} VND</p>
            <div class="product-card-actions">
                <button class="btn-icon-cart" onclick="addToCart('${p.itemType}', ${p.id})" title="Thêm vào giỏ hàng" aria-label="Thêm vào giỏ hàng"><i class="fas fa-cart-plus"></i></button>
                <button class="btn-add-cart" onclick="buyNow('${p.itemType}', ${p.id})" style="flex:1;">Mua ngay</button>
            </div>
        `;
        card.querySelector('.product-title').textContent = title;
        grid.appendChild(card);
    });
}

async function addToCart(itemType, id) {
    if (!window.PawConnectCart) {
        alert('Chức năng giỏ hàng đang được tải. Vui lòng tải lại trang rồi thử lại.');
        return false;
    }
    try {
        return await window.PawConnectCart.add(itemType, id);
    } catch (e) {
        alert('Không thể thêm vào giỏ hàng. Vui lòng thử lại.');
        return false;
    }
}

// --- BOOKING PAGE LOGIC (MULTI-STEP) ---
async function loadServices() {
    try {
        const res = await fetch('/api/service-types');
        const services = await res.json();
        const select = document.getElementById('serviceSelect');
        services.forEach(s => {
            const opt = document.createElement('option');
            opt.value = s.id;
            opt.textContent = `${s.name} - ${s.price.toLocaleString()} VND`;
            select.appendChild(opt);
        });
    } catch(e) { console.error(e) }
}

function nextStep(step) {
    if (step === 3) {
        // Populate confirmation step
        const bSel = document.getElementById('branchSelect');
        const sSel = document.getElementById('serviceSelect');
        const time = document.getElementById('bookingTime').value;
        
        if (!time) return alert("Please select a valid time");

        document.getElementById('confirmBranch').textContent = bSel.options[bSel.selectedIndex].text;
        document.getElementById('confirmService').textContent = sSel.options[sSel.selectedIndex].text;
        document.getElementById('confirmTime').textContent = time.replace('T', ' ');
    }
    
    // Update classes
    document.querySelectorAll('.step-content').forEach(el => el.classList.remove('active'));
    document.getElementById(`step-${step}`).classList.add('active');
    
    document.querySelectorAll('.step').forEach((el, index) => {
        if (index < step) el.classList.add('active');
        else el.classList.remove('active');
    });
}

function prevStep(step) {
    document.querySelectorAll('.step-content').forEach(el => el.classList.remove('active'));
    document.getElementById(`step-${step}`).classList.add('active');
    
    document.querySelectorAll('.step').forEach((el, index) => {
        if (index < step) el.classList.add('active');
        else el.classList.remove('active');
    });
}

async function submitBooking() {
    const branchId = document.getElementById('branchSelect').value;
    const serviceTypeId = document.getElementById('serviceSelect').value;
    const time = document.getElementById('bookingTime').value;

    const request = {
        branchId: parseInt(branchId),
        serviceTypeId: parseInt(serviceTypeId),
        bookingTime: time
    };

    const resultDiv = document.getElementById('bookingResult');
    const submitBtn = document.getElementById('submitBtn');
    resultDiv.textContent = 'Processing...';
    resultDiv.style.color = 'var(--text-main)';
    submitBtn.disabled = true;

    try {
        const accessToken = localStorage.getItem('pawconnect.accessToken');
        if (!accessToken) {
            window.location.assign('/auth/login?returnTo=%2Fservice');
            return;
        }
        const res = await fetch('/api/bookings', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${accessToken}`
            },
            body: JSON.stringify(request)
        });
        
        if (res.ok) {
            resultDiv.textContent = 'Booking successful! We will see you soon.';
            resultDiv.style.color = 'var(--success-color)';
            submitBtn.style.display = 'none';
        } else {
            resultDiv.textContent = 'Time conflict or error occurred.';
            resultDiv.style.color = 'var(--primary-color)';
            submitBtn.disabled = false;
        }
    } catch (e) {
        resultDiv.textContent = 'Network error.';
        submitBtn.disabled = false;
    }
}

async function requireBookingLogin() {
    const accessToken = localStorage.getItem('pawconnect.accessToken');
    if (!accessToken) {
        window.location.replace('/auth/login?returnTo=%2Fservice');
        return null;
    }

    try {
        const response = await fetch('/api/auth/me', {
            headers: { Authorization: `Bearer ${accessToken}` }
        });
        if (response.ok) return await response.json();
    } catch (_) {
        // A failed validation is handled as an unavailable session below.
    }

    ['pawconnect.accessToken', 'pawconnect.refreshToken', 'accessToken', 'refreshToken', 'token']
        .forEach((key) => localStorage.removeItem(key));
    window.location.replace('/auth/login?returnTo=%2Fservice');
    return null;
}

function showBookingAccessMessage(message) {
    const wrapper = document.querySelector('.booking-form-wrapper');
    if (!wrapper) return;
    wrapper.innerHTML = `<div class="booking-access-message"><i class="fas fa-circle-info"></i><p>${message}</p></div>`;
}

// --- MODAL & BUY NOW LOGIC ---
function openModal(p) {
    document.getElementById('modalImage').src = p.imageUrl || 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?q=80&w=800&auto=format&fit=crop';
    
    const title = p.itemType === 'puppy' ? p.listingTitle : p.name;
    const price = p.itemType === 'puppy' ? p.pricePerPuppyVnd : p.price;
    
    document.getElementById('modalName').textContent = title;
    document.getElementById('modalPrice').textContent = price.toLocaleString() + ' VND';
    const modalBranch = document.getElementById('modalBranch');
    if (modalBranch) {
        modalBranch.textContent = getBranchName(p.branchId);
    }
    document.getElementById('modalBreed').textContent = p.breedCode || p.breed || 'Chưa cập nhật';
    document.getElementById('modalAge').textContent = p.ageMonths ? p.ageMonths + ' months' : (p.age || 'Chưa cập nhật');
    document.getElementById('modalHealth').textContent = p.healthStatus || 'Đang cập nhật';
    document.getElementById('modalCare').textContent = p.careInstructions || 'Liên hệ để biết thêm chi tiết';
    
    document.getElementById('modalCartBtn').onclick = () => addToCart(p.itemType, p.id);
    document.getElementById('modalConsultBtn').onclick = () => {
        if (!window.PawConnectChat) {
            alert('Chức năng tin nhắn đang được tải. Vui lòng thử lại sau ít giây.');
            return;
        }
        closeModal();
        window.PawConnectChat.openSupport({
            branchId: p.branchId,
            productId: p.itemType === 'product' ? p.id : null,
            puppyListingId: p.itemType === 'puppy' ? p.id : null,
            title
        });
    };
    document.getElementById('modalBuyBtn').onclick = () => buyNow(p.itemType, p.id);
    
    document.getElementById('productModal').classList.add('active');
}

function closeModal() {
    document.getElementById('productModal').classList.remove('active');
    clearItemDeepLink();
}

async function openLinkedItemModal() {
    const params = new URLSearchParams(window.location.search);
    const itemType = params.get('itemType');
    const itemId = Number(params.get('itemId'));
    if (!['product', 'puppy'].includes(itemType) || !Number.isSafeInteger(itemId) || itemId <= 0) return;

    const endpoint = itemType === 'product' ? `/api/products/${itemId}` : `/api/puppy-listings/${itemId}`;
    try {
        const response = await fetch(endpoint);
        if (!response.ok) throw new Error(`Item request failed with status ${response.status}`);
        const item = await response.json();
        item.itemType = itemType;
        openModal(item);
    } catch (_) {
        showLinkedItemNotice('Sản phẩm bạn muốn xem không còn khả dụng hoặc đã bị xóa.');
    }
}

function clearItemDeepLink() {
    const url = new URL(window.location.href);
    if (!url.searchParams.has('itemType') && !url.searchParams.has('itemId')) return;
    url.searchParams.delete('itemType');
    url.searchParams.delete('itemId');
    const query = url.searchParams.toString();
    window.history.replaceState({}, '', `${url.pathname}${query ? `?${query}` : ''}${url.hash}`);
}

function showLinkedItemNotice(message) {
    const grid = document.getElementById('productGrid');
    if (!grid) return;
    let notice = document.getElementById('shopDeepLinkNotice');
    if (!notice) {
        notice = document.createElement('p');
        notice.id = 'shopDeepLinkNotice';
        notice.className = 'shop-deep-link-notice';
        grid.before(notice);
    }
    notice.textContent = message;
}

async function buyNow(itemType, id) {
    const added = await addToCart(itemType, id);
    if (added) window.location.assign('/checkout');
}

function escapeHtml(value) {
    const element = document.createElement('div');
    element.textContent = value;
    return element.innerHTML;
}
