// --- SHOP PAGE LOGIC ---
document.addEventListener('DOMContentLoaded', async () => {
    // Determine which page we are on
    if (document.getElementById('productGrid')) {
        await loadBranches('branchFilter');
        await loadProducts();
        document.getElementById('branchFilter').addEventListener('change', loadProducts);
        document.getElementById('typeFilter').addEventListener('change', loadProducts);
    }
    
    if (document.getElementById('branchSelect')) {
        await loadBranches('branchSelect');
        await loadServices();
    }
});

async function loadBranches(elementId) {
    try {
        const res = await fetch('/api/branches');
        const branches = await res.json();
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
    const isBreedingDog = document.getElementById('typeFilter').value;
    
    let url = '/api/products?';
    if (branchId) url += `branchId=${branchId}&`;
    if (isBreedingDog) url += `isBreedingDog=${isBreedingDog}`;

    try {
        const res = await fetch(url);
        const products = await res.json();
        renderProducts(products);
    } catch (e) {
        document.getElementById('productGrid').innerHTML = '<p>Error loading products</p>';
    }
}

function renderProducts(products) {
    const grid = document.getElementById('productGrid');
    grid.innerHTML = '';
    
    if(products.length === 0) {
        grid.innerHTML = '<p>No items found.</p>';
        return;
    }

    products.forEach(p => {
        const card = document.createElement('div');
        card.className = 'product-card';
        if (p.isBreedingDog) card.innerHTML += `<div class="product-badge">Pet</div>`;
        
        const safeProductJson = JSON.stringify(p).replace(/"/g, '&quot;');
        
        card.innerHTML += `
            <img src="${p.imageUrl || 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?q=80&w=800&auto=format&fit=crop'}" alt="product" class="product-image" onclick="openModal(${safeProductJson})" style="cursor:pointer">
            <h3 class="product-title" onclick="openModal(${safeProductJson})" style="cursor:pointer"></h3>
            <p class="product-price">$${p.price}</p>
            <div class="product-card-actions">
                <button class="btn-icon-cart" onclick="addToCart(${p.id})" title="Add to Cart"><i class="fas fa-cart-plus"></i></button>
                <button class="btn-add-cart" onclick="buyNow(${p.id})" style="flex:1;">Mua ngay</button>
            </div>
        `;
        card.querySelector('.product-title').textContent = p.name;
        grid.appendChild(card);
    });
}

async function addToCart(productId) {
    try {
        const res = await fetch(`/api/cart/items?productId=${productId}&quantity=1`, { method: 'POST' });
        if (res.ok) {
            let countSpan = document.getElementById('cartCount');
            countSpan.textContent = parseInt(countSpan.textContent) + 1;
            
            // Add a small bounce animation
            countSpan.style.transform = 'scale(1.5)';
            setTimeout(() => countSpan.style.transform = 'scale(1)', 200);
        } else {
            alert('Failed to add to cart (out of stock)');
        }
    } catch (e) {
        alert('Error adding to cart');
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
            opt.textContent = `${s.name} - $${s.price}`;
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
        const res = await fetch('/api/bookings', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
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

// --- MODAL & BUY NOW LOGIC ---
function openModal(p) {
    document.getElementById('modalImage').src = p.imageUrl || 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?q=80&w=800&auto=format&fit=crop';
    document.getElementById('modalName').textContent = p.name;
    document.getElementById('modalPrice').textContent = '$' + p.price;
    document.getElementById('modalBreed').textContent = p.breed || 'Chưa cập nhật';
    document.getElementById('modalAge').textContent = p.age || 'Chưa cập nhật';
    document.getElementById('modalHealth').textContent = p.healthStatus || 'Đang cập nhật';
    document.getElementById('modalCare').textContent = p.careInstructions || 'Liên hệ để biết thêm chi tiết';
    
    document.getElementById('modalCartBtn').onclick = () => addToCart(p.id);
    document.getElementById('modalBuyBtn').onclick = () => buyNow(p.id);
    
    document.getElementById('productModal').classList.add('active');
}

function closeModal() {
    document.getElementById('productModal').classList.remove('active');
}

async function buyNow(productId) {
    // Add to cart then go to a mock checkout or show alert
    await addToCart(productId);
    alert('Đã thêm vào giỏ hàng. Đang chuyển đến thanh toán...');
    // window.location.href = '/checkout'; // To be implemented by TV2/3
}
