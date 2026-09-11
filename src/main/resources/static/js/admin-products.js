document.addEventListener('DOMContentLoaded', () => {
    // 1. Check Auth (JWT)
    const token = localStorage.getItem('token');
    // For local testing, we might not have a login page yet. 
    // Just add token to headers if it exists.
    
    // 2. Load Initial Data
    loadProducts();
    loadCategories();
    loadBranches();

    // 3. Setup Modal
    const modal = document.getElementById('addProductModal');
    const addBtn = document.getElementById('addProductBtn');
    const closeBtns = document.querySelectorAll('.close-btn');

    addBtn.addEventListener('click', () => {
        const form = document.getElementById('addProductForm');
        form.reset();
        delete form.dataset.editId;
        delete form.dataset.oldImageUrl;
        document.getElementById('uploadStatus').textContent = '';
        document.querySelector('#addProductModal .modal-header h2').textContent = 'Add New Product / Dog';
        document.getElementById('p_imageFile').required = true;
        modal.classList.add('active');
    });

    closeBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            modal.classList.remove('active');
        });
    });

    // Handle Product Type Change
    const typeSelect = document.getElementById('p_productType');
    typeSelect.addEventListener('change', (e) => {
        updateDynamicFields(e.target.value);
    });

    // 4. Handle Form Submit (Upload image first, then save product)
    document.getElementById('addProductForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const saveBtn = document.getElementById('saveProductBtn');
        const statusText = document.getElementById('uploadStatus');
        
        try {
            saveBtn.disabled = true;
            statusText.textContent = 'Uploading image...';

            // Step A: Upload Image
            const fileInput = document.getElementById('p_imageFile');
            const file = fileInput.files[0];
            const formElement = document.getElementById('addProductForm');
            let imageUrl = formElement.dataset.oldImageUrl || '';

            if (file) {
                const formData = new FormData();
                formData.append('file', file);
                
                const uploadRes = await fetch('/api/upload', {
                    method: 'POST',
                    headers: {
                        'Authorization': token ? `Bearer ${token}` : ''
                    },
                    body: formData
                });

                if (!uploadRes.ok) {
                    let errMsg = 'Image upload failed';
                    try {
                        const errData = await uploadRes.json();
                        if (errData.message) errMsg = errData.message;
                    } catch (e) {}
                    throw new Error(errMsg);
                }
                const uploadData = await uploadRes.json();
                imageUrl = uploadData.url;
            }

            statusText.textContent = 'Saving product data...';

            // Step B: Save Product
            const productType = document.getElementById('p_productType').value;
            const productRequest = {
                name: document.getElementById('p_name').value,
                description: document.getElementById('p_description').value,
                price: parseFloat(document.getElementById('p_price').value),
                stock: parseInt(document.getElementById('p_stock').value),
                imageUrl: imageUrl,
                categoryId: parseInt(document.getElementById('p_categoryId').value),
                branchId: parseInt(document.getElementById('p_branchId').value),
                isBreedingDog: productType === 'dog',
                breed: productType === 'dog' ? document.getElementById('p_breed').value || null : null,
                age: productType === 'dog' ? document.getElementById('p_age').value || null : null,
                healthStatus: productType === 'dog' ? document.getElementById('p_healthStatus').value || null : null,
                careInstructions: productType === 'dog' ? document.getElementById('p_careInstructions').value || null : null,
                ingredients: productType === 'food' ? document.getElementById('p_ingredients').value || null : null,
                targetAudience: productType === 'food' ? document.getElementById('p_targetAudience').value || null : null
            };

            const isEdit = !!formElement.dataset.editId;
            const url = isEdit ? `/api/products/${formElement.dataset.editId}` : '/api/products';
            const method = isEdit ? 'PUT' : 'POST';

            const productRes = await fetch(url, {
                method: method,
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': token ? `Bearer ${token}` : ''
                },
                body: JSON.stringify(productRequest)
            });

            if (!productRes.ok) {
                const err = await productRes.json();
                throw new Error(err.message || 'Failed to save product');
            }

            // Success
            alert(isEdit ? 'Product updated successfully!' : 'Product added successfully!');
            modal.classList.remove('active');
            loadProducts(); // refresh list

        } catch (error) {
            alert(error.message);
            statusText.textContent = 'Error: ' + error.message;
        } finally {
            saveBtn.disabled = false;
        }
    });
});

async function loadProducts() {
    try {
        const res = await fetch('/api/products');
        const data = await res.json();
        
        const tbody = document.getElementById('productsTableBody');
        tbody.innerHTML = '';

        data.forEach(p => {
            const tr = document.createElement('tr');
            const defaultImg = 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?q=80&w=150&auto=format&fit=crop';
            tr.innerHTML = `
                <td><img src="${p.imageUrl || defaultImg}" class="product-img-cell" alt="${p.name}"></td>
                <td><strong>${p.name}</strong><br><small style="color:var(--text-muted)">${p.isBreedingDog ? 'Dog' : 'Product'}</small></td>
                <td>${p.category ? p.category.name : '-'}</td>
                <td>${p.price.toLocaleString()} VND</td>
                <td>${p.stock}</td>
                <td>
                    <button class="btn btn-secondary" onclick="editProduct(${p.id})" style="padding: 0.4rem 0.8rem; font-size: 0.8rem">Edit</button>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        console.error('Failed to load products', e);
    }
}

async function loadCategories() {
    try {
        const res = await fetch('/api/categories');
        const data = await res.json();
        const select = document.getElementById('p_categoryId');
        data.forEach(c => {
            const opt = document.createElement('option');
            opt.value = c.id;
            opt.textContent = c.name;
            select.appendChild(opt);
        });
    } catch (e) {
        console.error('Failed to load categories', e);
    }
}

async function loadBranches() {
    try {
        const res = await fetch('/api/branches');
        const data = await res.json();
        const select = document.getElementById('p_branchId');
        data.forEach(b => {
            const opt = document.createElement('option');
            opt.value = b.id;
            opt.textContent = b.name;
            select.appendChild(opt);
        });
    } catch (e) {
        console.error('Failed to load branches', e);
    }
}

async function editProduct(id) {
    try {
        const res = await fetch(`/api/products/${id}`);
        if (!res.ok) throw new Error('Failed to fetch product');
        const p = await res.json();
        
        const form = document.getElementById('addProductForm');
        form.dataset.editId = p.id;
        form.dataset.oldImageUrl = p.imageUrl || '';
        
        document.getElementById('p_name').value = p.name;
        document.getElementById('p_description').value = p.description || '';
        document.getElementById('p_price').value = p.price;
        document.getElementById('p_stock').value = p.stock;
        
        let pType = 'other';
        if (p.isBreedingDog) pType = 'dog';
        else if (p.ingredients || p.targetAudience) pType = 'food';
        
        document.getElementById('p_productType').value = pType;
        updateDynamicFields(pType);
        
        document.getElementById('p_breed').value = p.breed || '';
        document.getElementById('p_age').value = p.age || '';
        document.getElementById('p_healthStatus').value = p.healthStatus || '';
        document.getElementById('p_careInstructions').value = p.careInstructions || '';
        document.getElementById('p_ingredients').value = p.ingredients || '';
        document.getElementById('p_targetAudience').value = p.targetAudience || '';
        
        if (p.category) document.getElementById('p_categoryId').value = p.category.id;
        if (p.branch) document.getElementById('p_branchId').value = p.branch.id;
        
        document.getElementById('p_imageFile').required = false; // Not required on edit
        document.getElementById('uploadStatus').textContent = p.imageUrl ? '(Has existing image)' : '';
        
        document.querySelector('#addProductModal .modal-header h2').textContent = 'Edit Product / Dog';
        document.getElementById('addProductModal').classList.add('active');
    } catch (e) {
        alert('Error loading product details: ' + e.message);
    }
}

function updateDynamicFields(type) {
    const dogFields = document.querySelectorAll('.dog-fields');
    const foodFields = document.querySelectorAll('.food-fields');
    const lblDescription = document.getElementById('lbl_description');
    
    dogFields.forEach(f => f.style.display = 'none');
    foodFields.forEach(f => f.style.display = 'none');
    
    if (type === 'dog') {
        dogFields.forEach(f => f.style.display = 'flex');
        lblDescription.textContent = 'Special Notes (Lưu ý đặc biệt)';
    } else if (type === 'food') {
        foodFields.forEach(f => f.style.display = 'flex');
        lblDescription.textContent = 'Special Notes (Lưu ý đặc biệt)';
    } else {
        lblDescription.textContent = 'Usage Notes (Lưu ý khi sử dụng)';
    }
}
