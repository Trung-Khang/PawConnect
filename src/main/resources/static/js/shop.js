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
        initBookingDatePicker();
        const bSelect = document.getElementById('branchSelect');
        if (bSelect) {
            bSelect.addEventListener('change', () => {
                const step2 = document.getElementById('step-2');
                if (step2 && step2.classList.contains('active')) {
                    loadAvailableTimeSlots();
                }
            });
        }
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

// --- BOOKING PAGE LOGIC (MULTI-STEP & MANAGEMENT) ---

function switchServiceTab(tab) {
    const bookPanel = document.getElementById('service-book-panel');
    const historyPanel = document.getElementById('service-history-panel');
    const btnBook = document.getElementById('tab-book-view-btn');
    const btnHistory = document.getElementById('tab-history-view-btn');

    if (!bookPanel || !historyPanel) return;

    if (tab === 'history') {
        bookPanel.style.display = 'none';
        historyPanel.style.display = 'block';
        if (btnBook) btnBook.classList.remove('active');
        if (btnHistory) btnHistory.classList.add('active');
        loadMyBookings();
    } else {
        bookPanel.style.display = 'block';
        historyPanel.style.display = 'none';
        if (btnBook) btnBook.classList.add('active');
        if (btnHistory) btnHistory.classList.remove('active');
    }
}

const OPERATING_SLOTS = [
    '07:00', '08:00', '09:00', '10:00', '11:00', '12:00',
    '13:00', '14:00', '15:00', '16:00', '17:00', '18:00',
    '19:00', '20:00'
];

function initBookingDatePicker() {
    const dateInput = document.getElementById('bookingDateInput');
    if (!dateInput) return;
    const now = new Date();
    const pad = n => String(n).padStart(2, '0');
    const todayStr = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
    dateInput.min = todayStr;
    if (!dateInput.value) {
        if (now.getHours() >= 20) {
            const tmr = new Date(now);
            tmr.setDate(tmr.getDate() + 1);
            dateInput.value = `${tmr.getFullYear()}-${pad(tmr.getMonth() + 1)}-${pad(tmr.getDate())}`;
        } else {
            dateInput.value = todayStr;
        }
    }
}

function onBookingDateChange() {
    const timeInput = document.getElementById('bookingTime');
    if (timeInput) timeInput.value = '';
    const selectedBox = document.getElementById('selectedSlotInfo');
    if (selectedBox) selectedBox.style.display = 'none';
    const msg = document.getElementById('timeValidationMsg');
    if (msg) msg.hidden = true;

    loadAvailableTimeSlots();
}

async function loadAvailableTimeSlots() {
    const grid = document.getElementById('timeSlotsGrid');
    const dateInput = document.getElementById('bookingDateInput');
    const branchSelect = document.getElementById('branchSelect');
    if (!grid || !dateInput || !branchSelect) return;

    const dateVal = dateInput.value;
    const branchId = branchSelect.value;

    if (!dateVal) {
        grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 1rem;">Vui lòng chọn ngày để xem lịch trống.</p>';
        return;
    }

    grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 1rem;"><i class="fas fa-spinner fa-spin"></i> Đang kiểm tra lịch trống…</p>';

    try {
        const res = await fetch(`/api/bookings/busy-slots?branchId=${branchId}&date=${dateVal}`);
        let busyList = [];
        if (res.ok) {
            busyList = await res.json();
        }

        const busyHours = new Set();
        if (Array.isArray(busyList)) {
            busyList.forEach(b => {
                if (b.bookingTime) {
                    const bt = new Date(b.bookingTime);
                    if (!isNaN(bt.getTime())) {
                        const h = String(bt.getHours()).padStart(2, '0') + ':00';
                        busyHours.add(h);
                    }
                }
            });
        }

        const now = new Date();
        const pad = n => String(n).padStart(2, '0');
        const todayStr = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
        const isToday = (dateVal === todayStr);
        const currentHour = now.getHours();

        grid.replaceChildren();

        const currentSelectedTime = document.getElementById('bookingTime')?.value || '';

        OPERATING_SLOTS.forEach(timeStr => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'time-slot-btn';
            btn.setAttribute('data-time', timeStr);

            const slotHour = parseInt(timeStr.split(':')[0], 10);

            if (isToday && slotHour <= currentHour) {
                btn.classList.add('slot-past');
                btn.disabled = true;
                btn.innerHTML = `
                    <span>${timeStr}</span>
                    <span class="slot-status-text">Đã qua</span>
                `;
            } else if (busyHours.has(timeStr)) {
                btn.classList.add('slot-busy');
                btn.disabled = true;
                btn.innerHTML = `
                    <span>${timeStr}</span>
                    <span class="slot-status-text">Đã kín</span>
                `;
            } else {
                const fullDateTime = `${dateVal}T${timeStr}:00`;
                const isSelected = (currentSelectedTime === fullDateTime);
                if (isSelected) {
                    btn.classList.add('slot-selected');
                }
                btn.innerHTML = `
                    <span>${timeStr}</span>
                    <span class="slot-status-text">${isSelected ? 'Đang chọn' : 'Còn trống'}</span>
                `;
                btn.onclick = () => selectTimeSlot(dateVal, timeStr, btn);
            }

            grid.appendChild(btn);
        });

    } catch (e) {
        console.error('Lỗi khi tải lịch trống:', e);
        grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--danger); padding: 1rem;">Không thể tải lịch trống lúc này. Vui lòng thử lại.</p>';
    }
}

function selectTimeSlot(dateVal, timeStr, selectedBtn) {
    const grid = document.getElementById('timeSlotsGrid');
    if (grid) {
        grid.querySelectorAll('.time-slot-btn').forEach(btn => {
            if (!btn.disabled) {
                btn.classList.remove('slot-selected');
                const statusSpan = btn.querySelector('.slot-status-text');
                if (statusSpan) statusSpan.textContent = 'Còn trống';
            }
        });
    }

    selectedBtn.classList.add('slot-selected');
    const curStatus = selectedBtn.querySelector('.slot-status-text');
    if (curStatus) curStatus.textContent = 'Đang chọn';

    const fullIsoTime = `${dateVal}T${timeStr}:00`;
    const timeInput = document.getElementById('bookingTime');
    if (timeInput) {
        timeInput.value = fullIsoTime;
    }

    const [year, month, day] = dateVal.split('-');
    const selectedBox = document.getElementById('selectedSlotInfo');
    const selectedText = document.getElementById('selectedSlotText');
    if (selectedBox && selectedText) {
        selectedText.textContent = `Khung giờ đã chọn: ${timeStr} ngày ${day}/${month}/${year}`;
        selectedBox.style.display = 'flex';
    }

    const msg = document.getElementById('timeValidationMsg');
    if (msg) msg.hidden = true;
}

async function loadServices() {
    try {
        const res = await fetch('/api/service-types');
        if (!res.ok) return;
        const services = await res.json();
        const select = document.getElementById('serviceSelect');
        if (!select) return;
        select.replaceChildren();
        services.forEach(s => {
            const opt = document.createElement('option');
            opt.value = s.id;
            opt.textContent = `${s.name} - ${s.price.toLocaleString('vi-VN')} VND (${s.duration} phút)`;
            select.appendChild(opt);
        });
    } catch(e) { console.error('Lỗi khi tải loại dịch vụ', e); }
}

function nextStep(step) {
    if (step === 2) {
        initBookingDatePicker();
        loadAvailableTimeSlots();
    }
    if (step === 3) {
        const bSel = document.getElementById('branchSelect');
        const sSel = document.getElementById('serviceSelect');
        const timeInput = document.getElementById('bookingTime');
        const time = timeInput ? timeInput.value : '';
        const msg = document.getElementById('timeValidationMsg');

        if (!time) {
            if (msg) {
                msg.textContent = 'Vui lòng chọn một khung giờ còn trống trong danh sách.';
                msg.hidden = false;
            }
            alert('Vui lòng chọn một khung giờ hẹn còn trống trước khi tiếp tục!');
            return;
        }

        const selected = new Date(time);
        const now = new Date();

        if (selected <= now) {
            if (msg) {
                msg.textContent = 'Thời gian đặt lịch không được ở quá khứ. Vui lòng chọn khung giờ trong tương lai.';
                msg.hidden = false;
            }
            alert('Thời gian đặt lịch không được ở quá khứ. Vui lòng chọn lại khung giờ!');
            return;
        }

        if (msg) msg.hidden = true;
        document.getElementById('confirmBranch').textContent = bSel.options[bSel.selectedIndex]?.text || 'Chưa chọn';
        document.getElementById('confirmService').textContent = sSel.options[sSel.selectedIndex]?.text || 'Chưa chọn';

        const pad = n => String(n).padStart(2, '0');
        const formatted = `${pad(selected.getDate())}/${pad(selected.getMonth()+1)}/${selected.getFullYear()} ${pad(selected.getHours())}:${pad(selected.getMinutes())}`;
        document.getElementById('confirmTime').textContent = formatted;
    }

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
    resultDiv.textContent = 'Đang xử lý đặt lịch…';
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
            resultDiv.innerHTML = `
                <div style="background:#e8f5e9; color:#2e7d32; padding:1.25rem; border-radius:10px; border-left:4px solid #4caf50; line-height: 1.6;">
                    <i class="fas fa-circle-check" style="font-size: 1.2rem;"></i> <strong>Đặt lịch hẹn thành công!</strong><br>
                    Chi nhánh đã nhận được lịch hẹn của bạn và nhân viên sẽ tiến hành xác nhận sớm nhất.<br>
                    <button type="button" class="btn-pill" style="margin-top:0.9rem;" onclick="switchServiceTab('history')">
                        <i class="fas fa-clock-rotate-left"></i> Xem lịch hẹn của bạn
                    </button>
                </div>
            `;
            submitBtn.style.display = 'none';
        } else {
            const err = await res.json().catch(() => ({}));
            resultDiv.textContent = err.message || 'Xảy ra lỗi khi đặt lịch hẹn. Vui lòng kiểm tra lại.';
            resultDiv.style.color = '#e27b58';
            submitBtn.disabled = false;
        }
    } catch (e) {
        resultDiv.textContent = 'Lỗi kết nối mạng. Vui lòng thử lại.';
        submitBtn.disabled = false;
    }
}

async function loadMyBookings() {
    const list = document.getElementById('my-bookings-list');
    if (!list) return;
    list.innerHTML = '<p style="text-align: center; color: var(--text-muted); padding: 2rem;">Đang tải lịch hẹn…</p>';

    const accessToken = localStorage.getItem('pawconnect.accessToken');
    if (!accessToken) {
        list.innerHTML = '<p style="text-align:center;"><a href="/auth/login?returnTo=%2Fservice">Đăng nhập</a> để xem lịch hẹn của bạn.</p>';
        return;
    }

    try {
        const res = await fetch('/api/bookings/my', {
            headers: { Authorization: `Bearer ${accessToken}` }
        });
        if (!res.ok) throw new Error('Không thể tải danh sách lịch hẹn.');
        const bookings = await res.json();
        list.replaceChildren();

        if (!bookings.length) {
            list.innerHTML = `
                <div style="text-align: center; padding: 3rem 1rem; background: #fffaf7; border-radius: 12px; border: 1px dashed #e5d5cc;">
                    <i class="fas fa-calendar-xmark" style="font-size: 2.5rem; color: #d0c5bf; margin-bottom: 0.75rem;"></i>
                    <p style="color: var(--text-muted); font-size: 1.05rem;">Bạn chưa có lịch hẹn dịch vụ nào.</p>
                    <button type="button" class="btn-pill" style="margin-top: 0.5rem;" onclick="switchServiceTab('book')">
                        <i class="fas fa-calendar-plus"></i> Đặt lịch hẹn ngay
                    </button>
                </div>`;
            return;
        }

        const statusLabelsMap = {
            PENDING: 'Chờ xác nhận',
            CONFIRMED: 'Đã xác nhận',
            ARRIVED: 'Khách đã tới',
            COMPLETED: 'Đã hoàn thành',
            NO_SHOW: 'Khách không tới',
            CANCELLED: 'Đã từ chối / Đã hủy'
        };

        bookings.forEach(b => {
            const card = document.createElement('div');
            card.className = 'my-booking-card';

            const statusText = statusLabelsMap[b.status] || b.status;
            let statusNoticeHtml = '';
            let cancelBtnHtml = '';

            if (b.status === 'CONFIRMED') {
                statusNoticeHtml = `
                    <div class="booking-confirmed-alert">
                        <i class="fas fa-circle-check"></i> <strong>Lịch hẹn đã được chi nhánh xác nhận!</strong> Vui lòng mang bé đến đúng giờ hẹn để được phục vụ tốt nhất.
                    </div>`;
            } else if (b.status === 'CANCELLED') {
                statusNoticeHtml = `
                    <div class="booking-reason-alert">
                        <i class="fas fa-circle-xmark"></i> <strong>Lý do từ chối / hủy:</strong> ${escapeHtml(b.reason || 'Lịch hẹn chưa phù hợp hoặc chi nhánh bận.')}
                    </div>`;
            } else if (b.status === 'PENDING') {
                cancelBtnHtml = `<button type="button" class="btn-pill btn-outline" style="border-color:#ef5350; color:#c62828; font-size:0.85rem;" onclick="cancelMyServiceBooking(${b.id})">Hủy hẹn</button>`;
            }

            const formattedTime = formatDateTimeVi(b.bookingTime);
            const formattedCreated = formatDateTimeVi(b.createdAt);

            card.innerHTML = `
                <div class="my-booking-info" style="flex: 1;">
                    <div style="display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap;">
                        <h4>#${b.id} - ${escapeHtml(b.serviceTypeName || 'Dịch vụ')}</h4>
                        <span class="booking-status-pill ${b.status}">${statusText}</span>
                    </div>
                    <div class="my-booking-meta">
                        <span><i class="fas fa-location-dot"></i> ${escapeHtml(b.branchName || `Chi nhánh #${b.branchId}`)}</span>
                        <span><i class="fas fa-clock"></i> <strong>Hẹn:</strong> ${formattedTime}</span>
                        ${b.servicePrice ? `<span><i class="fas fa-tag"></i> ${new Intl.NumberFormat('vi-VN').format(b.servicePrice)} VND</span>` : ''}
                        <span><i class="fas fa-calendar-day"></i> Đặt lúc: ${formattedCreated}</span>
                    </div>
                    ${statusNoticeHtml}
                </div>
                <div style="display: flex; gap: 0.5rem; align-items: center;">
                    ${cancelBtnHtml}
                </div>
            `;
            list.appendChild(card);
        });

    } catch (e) {
        list.innerHTML = `<p style="text-align: center; color: var(--danger); padding: 2rem;">${e.message}</p>`;
    }
}

async function cancelMyServiceBooking(id) {
    if (!confirm(`Bạn có chắc chắn muốn hủy lịch hẹn #${id}?`)) return;
    const accessToken = localStorage.getItem('pawconnect.accessToken');
    try {
        const res = await fetch(`/api/bookings/${id}/cancel`, {
            method: 'POST',
            headers: {
                Authorization: `Bearer ${accessToken}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ reason: 'Khách hàng tự hủy qua giao diện' })
        });
        if (res.ok) {
            alert(`Đã hủy lịch hẹn #${id}.`);
            await loadMyBookings();
        } else {
            const err = await res.json().catch(() => ({}));
            alert(err.message || 'Không thể hủy lịch hẹn.');
        }
    } catch (e) {
        alert('Lỗi kết nối khi hủy lịch hẹn.');
    }
}

function formatDateTimeVi(val) {
    if (!val) return '—';
    const d = new Date(val);
    if (isNaN(d.getTime())) return val;
    return new Intl.DateTimeFormat('vi-VN', {
        dateStyle: 'short',
        timeStyle: 'short'
    }).format(d);
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

