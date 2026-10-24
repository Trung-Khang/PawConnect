(() => {
    const tokenKey = 'pawconnect.accessToken';
    const selectedIds = new Set();
    let products = [];
    let canDeleteProducts = false;
    let canManageProducts = false;

    document.addEventListener('DOMContentLoaded', initialise);

    async function initialise() {
        bindEvents();
        try {
            const response = await fetch('/api/auth/me', { headers: authHeaders() });
            if (response.ok) {
                const role = (await response.json()).role;
                canDeleteProducts = role === 'ADMIN';
                canManageProducts = canDeleteProducts || role === 'BRANCH_MANAGER';
            }
        } catch (_) {
            canDeleteProducts = false;
            canManageProducts = false;
        }
        document.getElementById('addProductBtn').hidden = !canManageProducts;
        document.getElementById('selectAllProducts').hidden = !canDeleteProducts;
        document.getElementById('deleteSelectedBtn').hidden = !canDeleteProducts;
        loadProducts();
        loadCategories();
        loadBranches();
    }

    function bindEvents() {
        const modal = document.getElementById('addProductModal');
        document.getElementById('addProductBtn').addEventListener('click', () => openCreateModal(modal));
        document.querySelectorAll('.close-btn').forEach((button) => button.addEventListener('click', () => modal.classList.remove('active')));
        document.getElementById('p_productType').addEventListener('change', (event) => updateDynamicFields(event.target.value));
        document.getElementById('addProductForm').addEventListener('submit', saveProduct);
        document.getElementById('selectAllProducts').addEventListener('change', toggleAllProducts);
        document.getElementById('deleteSelectedBtn').addEventListener('click', deleteSelectedProducts);
    }

    function authHeaders() {
        const token = localStorage.getItem(tokenKey);
        return token ? { Authorization: `Bearer ${token}` } : {};
    }

    async function loadProducts() {
        try {
            const response = await fetch('/api/products');
            if (!response.ok) throw new Error('Không thể tải danh sách sản phẩm.');
            products = await response.json();
            selectedIds.clear();
            renderProducts();
        } catch (error) {
            window.alert(error.message);
        }
    }

    function renderProducts() {
        const body = document.getElementById('productsTableBody');
        body.replaceChildren();
        products.forEach((product) => {
            const row = document.createElement('tr');
            const defaultImage = 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?q=80&w=150&auto=format&fit=crop';
            row.innerHTML = `
                <td class="selection-cell"><input class="product-selector" type="checkbox" value="${product.id}" aria-label="Chọn ${escapeHtml(product.name)}"></td>
                <td><img src="${escapeAttribute(product.imageUrl || defaultImage)}" class="product-img-cell" alt=""></td>
                <td><strong></strong><br><small>${product.ingredients || product.targetAudience ? 'Sản phẩm' : 'Mặt hàng cửa hàng'}</small></td>
                <td></td>
                <td>${formatCurrency(product.price)}</td>
                <td>${product.stock}</td>
                <td class="row-actions"><button class="btn btn-secondary edit-product" type="button">Sửa</button><button class="btn btn-danger delete-product" type="button">Xóa</button></td>`;
            row.querySelector('strong').textContent = product.name;
            row.children[3].textContent = product.categoryName || 'Chưa phân loại';
            if (canDeleteProducts) {
                row.querySelector('.product-selector').addEventListener('change', (event) => toggleProductSelection(product.id, event.target.checked));
                row.querySelector('.delete-product').addEventListener('click', () => deleteProducts([product.id]));
            } else {
                row.querySelector('.selection-cell').hidden = true;
                row.querySelector('.delete-product').hidden = true;
            }
            row.querySelector('.edit-product').hidden = !canManageProducts;
            row.querySelector('.edit-product').addEventListener('click', () => editProduct(product.id));
            body.append(row);
        });
        updateSelectionControls();
    }

    function toggleProductSelection(id, selected) {
        if (selected) selectedIds.add(id);
        else selectedIds.delete(id);
        updateSelectionControls();
    }

    function toggleAllProducts(event) {
        selectedIds.clear();
        if (event.target.checked) products.forEach((product) => selectedIds.add(product.id));
        document.querySelectorAll('.product-selector').forEach((box) => box.checked = selectedIds.has(Number(box.value)));
        updateSelectionControls();
    }

    function updateSelectionControls() {
        const all = document.getElementById('selectAllProducts');
        const deleteButton = document.getElementById('deleteSelectedBtn');
        all.checked = products.length > 0 && selectedIds.size === products.length;
        all.indeterminate = selectedIds.size > 0 && selectedIds.size < products.length;
        deleteButton.disabled = selectedIds.size === 0;
        deleteButton.innerHTML = `<i class="fas fa-trash"></i> Xóa đã chọn${selectedIds.size ? ` (${selectedIds.size})` : ''}`;
    }

    async function deleteSelectedProducts() {
        if (selectedIds.size) await deleteProducts([...selectedIds]);
    }

    async function deleteProducts(ids) {
        const label = ids.length === 1 ? 'mặt hàng này' : `${ids.length} mặt hàng đã chọn`;
        if (!window.confirm(`Bạn có chắc muốn xóa ${label}?`)) return;
        const response = await fetch('/api/products', {
            method: 'DELETE',
            headers: { ...authHeaders(), 'Content-Type': 'application/json' },
            body: JSON.stringify(ids)
        });
        if (!response.ok) return window.alert(await responseMessage(response, 'Không thể xóa sản phẩm.'));
        await loadProducts();
    }

    function openCreateModal(modal) {
        const form = document.getElementById('addProductForm');
        form.reset();
        delete form.dataset.editId;
        delete form.dataset.oldImageUrl;
        delete form.dataset.oldImagePublicId;
        document.getElementById('uploadStatus').textContent = '';
        document.querySelector('#addProductModal .modal-header h2').textContent = 'Thêm sản phẩm';
        document.getElementById('p_imageFile').required = true;
        updateDynamicFields(document.getElementById('p_productType').value);
        modal.classList.add('active');
    }

    async function saveProduct(event) {
        event.preventDefault();
        const form = document.getElementById('addProductForm');
        const saveButton = document.getElementById('saveProductBtn');
        const status = document.getElementById('uploadStatus');
        try {
            saveButton.disabled = true;
            let imageUrl = form.dataset.oldImageUrl || '';
            let imagePublicId = form.dataset.oldImagePublicId || '';
            const file = document.getElementById('p_imageFile').files[0];
            if (file) {
                status.textContent = 'Đang tải ảnh...';
                const formData = new FormData();
                formData.append('file', file);
                formData.append('assetKey', document.getElementById('p_name').value || 'product');
                if (imagePublicId) formData.append('previousPublicId', imagePublicId);
                const upload = await fetch(imagePublicId ? '/api/media/PRODUCT/replace' : '/api/media/PRODUCT', { method: 'POST', headers: authHeaders(), body: formData });
                if (!upload.ok) throw new Error(await responseMessage(upload, 'Không thể tải ảnh lên.'));
                const asset = await upload.json();
                imageUrl = asset.asset?.secureUrl || asset.secureUrl;
                imagePublicId = asset.asset?.publicId || asset.publicId;
            }
            const request = {
                name: document.getElementById('p_name').value.trim(),
                description: document.getElementById('p_description').value.trim(),
                price: Number(document.getElementById('p_price').value),
                stock: Number(document.getElementById('p_stock').value),
                imageUrl, imagePublicId,
                categoryId: Number(document.getElementById('p_categoryId').value),
                branchId: Number(document.getElementById('p_branchId').value),
                ingredients: document.getElementById('p_productType').value === 'food' ? document.getElementById('p_ingredients').value.trim() || null : null,
                targetAudience: document.getElementById('p_productType').value === 'food' ? document.getElementById('p_targetAudience').value.trim() || null : null
            };
            const editing = Boolean(form.dataset.editId);
            const response = await fetch(editing ? `/api/products/${form.dataset.editId}` : '/api/products', {
                method: editing ? 'PUT' : 'POST', headers: { ...authHeaders(), 'Content-Type': 'application/json' }, body: JSON.stringify(request)
            });
            if (!response.ok) throw new Error(await responseMessage(response, 'Không thể lưu sản phẩm.'));
            document.getElementById('addProductModal').classList.remove('active');
            await loadProducts();
        } catch (error) {
            status.textContent = error.message;
        } finally {
            saveButton.disabled = false;
        }
    }

    async function editProduct(id) {
        const response = await fetch(`/api/products/${id}`);
        if (!response.ok) return window.alert(await responseMessage(response, 'Không thể tải sản phẩm.'));
        const product = await response.json();
        const form = document.getElementById('addProductForm');
        form.dataset.editId = product.id;
        form.dataset.oldImageUrl = product.imageUrl || '';
        form.dataset.oldImagePublicId = product.imagePublicId || '';
        document.getElementById('p_name').value = product.name;
        document.getElementById('p_description').value = product.description || '';
        document.getElementById('p_price').value = product.price;
        document.getElementById('p_stock').value = product.stock;
        document.getElementById('p_categoryId').value = product.categoryId;
        document.getElementById('p_branchId').value = product.branchId;
        document.getElementById('p_productType').value = product.ingredients || product.targetAudience ? 'food' : 'other';
        document.getElementById('p_ingredients').value = product.ingredients || '';
        document.getElementById('p_targetAudience').value = product.targetAudience || '';
        document.getElementById('p_imageFile').required = false;
        updateDynamicFields(document.getElementById('p_productType').value);
        document.querySelector('#addProductModal .modal-header h2').textContent = 'Sửa sản phẩm';
        document.getElementById('addProductModal').classList.add('active');
    }

    async function loadCategories() {
        const response = await fetch('/api/categories');
        if (!response.ok) return;
        const select = document.getElementById('p_categoryId');
        select.replaceChildren();
        (await response.json()).forEach((category) => select.add(new Option(category.name, category.id)));
    }

    async function loadBranches() {
        const response = await fetch('/api/branches');
        if (!response.ok) return;
        const select = document.getElementById('p_branchId');
        select.replaceChildren();
        (await response.json()).forEach((branch) => select.add(new Option(branch.name, branch.id)));
    }

    function updateDynamicFields(type) {
        document.querySelectorAll('.dog-fields').forEach((field) => field.style.display = 'none');
        document.querySelectorAll('.food-fields').forEach((field) => field.style.display = type === 'food' ? 'flex' : 'none');
    }

    async function responseMessage(response, fallback) {
        try { return (await response.json()).message || fallback; } catch (_) { return fallback; }
    }

    function formatCurrency(value) { return `${new Intl.NumberFormat('vi-VN').format(value || 0)} VND`; }
    function escapeHtml(value) { const node = document.createElement('span'); node.textContent = value || ''; return node.innerHTML; }
    function escapeAttribute(value) { return String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;'); }
})();
