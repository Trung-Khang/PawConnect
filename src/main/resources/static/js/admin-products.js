(() => {
    const tokenKey = 'pawconnect.accessToken';
    const selectedKeys = new Set(); // store 'product_1' or 'puppy_2'
    let storeItems = [];
    let branchesMap = new Map();
    let categoriesList = [];
    let canDeleteProducts = false;
    let canManageProducts = false;
    let currentUser = null;

    document.addEventListener('DOMContentLoaded', initialise);

    async function initialise() {
        bindEvents();
        try {
            const response = await fetch('/api/auth/me', { headers: authHeaders() });
            if (response.ok) {
                currentUser = await response.json();
                const role = currentUser.role;
                canDeleteProducts = role === 'ADMIN';
                canManageProducts = role === 'ADMIN' || role === 'BRANCH_MANAGER';

                if (role === 'BRANCH_MANAGER' && (currentUser.branchName || currentUser.branchId)) {
                    const h1 = document.querySelector('.top-header h1');
                    if (h1) {
                        const bName = currentUser.branchName || ('Chi nhánh #' + currentUser.branchId);
                        h1.textContent = `Sản phẩm & Chó giống - ${bName}`;
                    }
                }

                if (role === 'ADMIN') {
                    const adminFilter = document.getElementById('adminBranchFilter');
                    if (adminFilter) {
                        adminFilter.style.display = 'inline-block';
                        adminFilter.addEventListener('change', () => loadAllStoreItems());
                    }
                }
            }
        } catch (_) {
            canDeleteProducts = false;
            canManageProducts = false;
        }

        const addBtn = document.getElementById('addProductBtn');
        const selectAll = document.getElementById('selectAllProducts');
        const deleteBtn = document.getElementById('deleteSelectedBtn');

        if (addBtn) addBtn.hidden = !canManageProducts;
        if (selectAll) selectAll.hidden = false;
        if (deleteBtn) deleteBtn.hidden = false;

        await loadBranches();
        await loadCategories();
        await loadAllStoreItems();
    }

    function bindEvents() {
        const modal = document.getElementById('addProductModal');
        document.getElementById('addProductBtn')?.addEventListener('click', () => openCreateModal(modal));
        document.querySelectorAll('.close-btn').forEach((button) => button.addEventListener('click', () => modal.classList.remove('active')));
        
        const catGroup = document.getElementById('p_categoryGroup');
        if (catGroup) {
            catGroup.addEventListener('change', (e) => onCategoryGroupChange(e.target.value));
        }

        document.getElementById('addProductForm')?.addEventListener('submit', saveStoreItem);
        document.getElementById('selectAllProducts')?.addEventListener('change', toggleAllItems);
        document.getElementById('deleteSelectedBtn')?.addEventListener('click', deleteSelectedItems);
    }

    function onCategoryGroupChange(type) {
        const breedInput = document.getElementById('p_breed');
        const dogFields = document.querySelectorAll('.dog-fields');
        if (type === 'puppy') {
            if (breedInput) {
                breedInput.disabled = false;
                breedInput.required = true;
                breedInput.placeholder = 'Nhập giống chó (VD: Corgi, Poodle, Golden...)';
            }
            dogFields.forEach(f => f.style.display = 'flex');
        } else {
            if (breedInput) {
                breedInput.disabled = true;
                breedInput.required = false;
                breedInput.value = '';
                breedInput.placeholder = 'Không áp dụng cho sản phẩm';
            }
            dogFields.forEach(f => f.style.display = 'none');
        }
    }

    function authHeaders() {
        const token = localStorage.getItem(tokenKey);
        return token ? { Authorization: `Bearer ${token}` } : {};
    }

    async function loadBranches() {
        try {
            const response = await fetch('/api/branches');
            if (!response.ok) return;
            const branches = await response.json();
            branchesMap.clear();

            const select = document.getElementById('p_branchId');
            const adminFilter = document.getElementById('adminBranchFilter');

            if (select) select.replaceChildren();
            if (adminFilter) {
                adminFilter.replaceChildren();
                adminFilter.add(new Option('Tất cả Chi nhánh', ''));
            }

            const isBranchManager = currentUser?.role === 'BRANCH_MANAGER' && currentUser?.branchId;

            branches.forEach((branch) => {
                branchesMap.set(branch.id, branch.name);
                if (adminFilter) adminFilter.add(new Option(branch.name, branch.id));

                if (select) {
                    if (!isBranchManager || branch.id === currentUser.branchId) {
                        select.add(new Option(branch.name, branch.id));
                    }
                }
            });

            if (isBranchManager && select) {
                select.value = currentUser.branchId;
                select.disabled = true;
            }
        } catch (_) {}
    }

    async function loadCategories() {
        try {
            const response = await fetch('/api/categories');
            if (!response.ok) return;
            categoriesList = await response.json();
        } catch (_) {}
    }

    async function loadAllStoreItems() {
        try {
            let branchParam = '';
            if (currentUser?.role === 'BRANCH_MANAGER' && currentUser?.branchId) {
                branchParam = `?branchId=${currentUser.branchId}`;
            } else if (currentUser?.role === 'ADMIN') {
                const adminFilter = document.getElementById('adminBranchFilter');
                if (adminFilter && adminFilter.value) {
                    branchParam = `?branchId=${adminFilter.value}`;
                }
            }

            const [productsRes, puppiesRes] = await Promise.all([
                fetch('/api/products' + branchParam),
                fetch('/api/puppy-listings' + branchParam)
            ]);

            storeItems = [];

            if (productsRes.ok) {
                const products = await productsRes.json();
                products.forEach(p => {
                    if (currentUser?.role === 'BRANCH_MANAGER' && currentUser?.branchId && p.branchId !== currentUser.branchId) {
                        return; // strictly enforce branch isolation
                    }
                    storeItems.push({
                        id: p.id,
                        itemType: 'product',
                        name: p.name,
                        price: p.price,
                        stock: p.stock,
                        imageUrl: p.imageUrl,
                        imagePublicId: p.imagePublicId,
                        branchId: p.branchId,
                        branchName: branchesMap.get(p.branchId) || '',
                        categoryId: p.categoryId,
                        categoryName: p.categoryName || 'Sản phẩm',
                        description: p.description
                    });
                });
            }

            if (puppiesRes.ok) {
                const puppies = await puppiesRes.json();
                puppies.forEach(pup => {
                    if (currentUser?.role === 'BRANCH_MANAGER' && currentUser?.branchId && pup.branchId !== currentUser.branchId) {
                        return; // strictly enforce branch isolation
                    }
                    storeItems.push({
                        id: pup.id,
                        itemType: 'puppy',
                        name: pup.listingTitle,
                        price: pup.pricePerPuppyVnd,
                        stock: pup.stock,
                        imageUrl: pup.imageUrl,
                        imagePublicId: pup.imagePublicId,
                        branchId: pup.branchId,
                        branchName: branchesMap.get(pup.branchId) || '',
                        categoryId: pup.categoryId,
                        categoryName: 'Chó con',
                        breedCode: pup.breedCode,
                        description: pup.description,
                        ageMonths: pup.ageMonths,
                        healthStatus: pup.healthStatus,
                        careInstructions: pup.careInstructions
                    });
                });
            }

            selectedKeys.clear();
            renderTable();
        } catch (error) {
            window.alert('Không thể tải danh sách cửa hàng: ' + error.message);
        }
    }

    function renderTable() {
        const body = document.getElementById('productsTableBody');
        if (!body) return;
        body.replaceChildren();

        const defaultImage = 'https://images.unsplash.com/photo-1583337130417-3346a1be7dee?q=80&w=150&auto=format&fit=crop';

        storeItems.forEach((item) => {
            const itemKey = `${item.itemType}_${item.id}`;
            const row = document.createElement('tr');
            row.innerHTML = `
                <td class="selection-cell">
                    <input class="product-selector" type="checkbox" value="${itemKey}" aria-label="Chọn ${escapeHtml(item.name)}">
                </td>
                <td>
                    <img src="${escapeAttribute(item.imageUrl || defaultImage)}" class="product-img-cell" alt="">
                </td>
                <td>
                    <strong>${escapeHtml(item.name)}</strong>
                </td>
                <td>
                    ${item.itemType === 'puppy'
                        ? `<span class="badge" style="background:#fff3ed;color:#e27b58;border:1.5px solid #e27b58;padding:3px 10px;border-radius:12px;font-weight:700;font-size:0.8rem;">Chó con · ${escapeHtml(item.breedCode || 'Chưa rõ')}</span>`
                        : `<span class="badge" style="background:#f0fdf4;color:#16a34a;border:1.5px solid #16a34a;padding:3px 10px;border-radius:12px;font-weight:700;font-size:0.8rem;">Thức ăn, phụ kiện</span>`
                    }
                </td>
                <td>
                    <span>${escapeHtml(item.branchName || ('Chi nhánh #' + item.branchId))}</span>
                </td>
                <td style="font-weight:700;color:#e27b58;">
                    ${formatCurrency(item.price)}
                </td>
                <td>
                    <strong>${item.stock}</strong>
                </td>
                <td class="row-actions">
                    <button class="btn btn-secondary edit-product" type="button">Sửa</button>
                    <button class="btn btn-danger delete-product" type="button">Xóa</button>
                </td>
            `;

            const selector = row.querySelector('.product-selector');
            const deleteBtn = row.querySelector('.delete-product');
            const editBtn = row.querySelector('.edit-product');

            selector.addEventListener('change', (e) => toggleItemSelection(itemKey, e.target.checked));
            deleteBtn.addEventListener('click', () => deleteItems([item]));

            editBtn.hidden = !canManageProducts;
            editBtn.addEventListener('click', () => openEditModal(item));

            body.append(row);
        });

        updateSelectionControls();
    }

    function toggleItemSelection(itemKey, selected) {
        if (selected) selectedKeys.add(itemKey);
        else selectedKeys.delete(itemKey);
        updateSelectionControls();
    }

    function toggleAllItems(event) {
        selectedKeys.clear();
        if (event.target.checked) {
            storeItems.forEach(item => selectedKeys.add(`${item.itemType}_${item.id}`));
        }
        document.querySelectorAll('.product-selector').forEach(cb => {
            cb.checked = selectedKeys.has(cb.value);
        });
        updateSelectionControls();
    }

    function updateSelectionControls() {
        const all = document.getElementById('selectAllProducts');
        const deleteButton = document.getElementById('deleteSelectedBtn');
        if (!all || !deleteButton) return;
        all.checked = storeItems.length > 0 && selectedKeys.size === storeItems.length;
        all.indeterminate = selectedKeys.size > 0 && selectedKeys.size < storeItems.length;
        deleteButton.disabled = selectedKeys.size === 0;
        deleteButton.innerHTML = `<i class="fas fa-trash"></i> Xóa đã chọn${selectedKeys.size ? ` (${selectedKeys.size})` : ''}`;
    }

    async function deleteSelectedItems() {
        if (!selectedKeys.size) return;
        const itemsToDelete = storeItems.filter(item => selectedKeys.has(`${item.itemType}_${item.id}`));
        await deleteItems(itemsToDelete);
    }

    async function deleteItems(items) {
        if (!items || items.length === 0) return;
        const label = items.length === 1 ? `mặt hàng "${items[0].name}"` : `${items.length} mặt hàng đã chọn`;
        if (!window.confirm(`Bạn có chắc muốn xóa ${label}?`)) return;

        const productIds = items.filter(it => it.itemType === 'product').map(it => it.id);
        const puppyIds = items.filter(it => it.itemType === 'puppy').map(it => it.id);

        try {
            if (productIds.length > 0) {
                const res = await fetch('/api/products', {
                    method: 'DELETE',
                    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
                    body: JSON.stringify(productIds)
                });
                if (!res.ok) {
                    if (res.status === 403) {
                        throw new Error('Chỉ Quản trị viên (ADMIN) mới có quyền xóa sản phẩm khỏi kho hàng. Nhân viên chi nhánh có thể cập nhật số lượng tồn kho về 0 (hết hàng) hoặc xóa bài Chó con của chi nhánh.');
                    }
                    throw new Error(await responseMessage(res, 'Không thể xóa một số sản phẩm.'));
                }
            }

            if (puppyIds.length > 0) {
                const res = await fetch('/api/puppy-listings', {
                    method: 'DELETE',
                    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
                    body: JSON.stringify(puppyIds)
                });
                if (!res.ok) throw new Error(await responseMessage(res, 'Không thể xóa một số chó con.'));
            }

            await loadAllStoreItems();
        } catch (error) {
            window.alert(error.message);
            await loadAllStoreItems();
        }
    }

    function openCreateModal(modal) {
        const form = document.getElementById('addProductForm');
        form.reset();
        delete form.dataset.editId;
        delete form.dataset.itemType;
        delete form.dataset.oldImageUrl;
        delete form.dataset.oldImagePublicId;
        document.getElementById('uploadStatus').textContent = '';
        document.querySelector('#addProductModal .modal-header h2').textContent = 'Thêm mặt hàng';
        document.getElementById('p_imageFile').required = true;
        document.getElementById('p_categoryGroup').value = 'product';
        onCategoryGroupChange('product');

        const branchSelect = document.getElementById('p_branchId');
        if (currentUser?.role === 'BRANCH_MANAGER' && currentUser?.branchId) {
            if (branchSelect) {
                branchSelect.value = currentUser.branchId;
                branchSelect.disabled = true;
            }
        } else if (branchSelect) {
            branchSelect.disabled = false;
        }

        modal.classList.add('active');
    }

    function openEditModal(item) {
        const form = document.getElementById('addProductForm');
        form.reset();
        form.dataset.editId = item.id;
        form.dataset.itemType = item.itemType;
        form.dataset.oldImageUrl = item.imageUrl || '';
        form.dataset.oldImagePublicId = item.imagePublicId || '';
        document.getElementById('uploadStatus').textContent = '';

        document.getElementById('p_name').value = item.name;

        const branchSelect = document.getElementById('p_branchId');
        if (currentUser?.role === 'BRANCH_MANAGER' && currentUser?.branchId) {
            if (branchSelect) {
                branchSelect.value = currentUser.branchId;
                branchSelect.disabled = true;
            }
        } else if (branchSelect) {
            branchSelect.value = item.branchId;
            branchSelect.disabled = false;
        }

        document.getElementById('p_price').value = item.price;
        document.getElementById('p_stock').value = item.stock;
        document.getElementById('p_description').value = item.description || '';
        document.getElementById('p_imageFile').required = false;

        document.getElementById('p_categoryGroup').value = item.itemType;
        onCategoryGroupChange(item.itemType);

        if (item.itemType === 'puppy') {
            document.getElementById('p_breed').value = item.breedCode || '';
            document.getElementById('p_age').value = item.ageMonths || '';
            document.getElementById('p_healthStatus').value = item.healthStatus || '';
            document.getElementById('p_careInstructions').value = item.careInstructions || '';
        }

        document.querySelector('#addProductModal .modal-header h2').textContent = 'Sửa mặt hàng';
        document.getElementById('addProductModal').classList.add('active');
    }

    async function saveStoreItem(event) {
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
                status.textContent = 'Đang tải ảnh lên Cloudinary...';
                const formData = new FormData();
                formData.append('file', file);
                const safeKey = 'store_' + Date.now() + '_' + Math.floor(Math.random() * 10000);
                formData.append('assetKey', safeKey);
                if (imagePublicId) formData.append('previousPublicId', imagePublicId);

                const upload = await fetch(imagePublicId ? '/api/media/PRODUCT/replace' : '/api/media/PRODUCT', {
                    method: 'POST',
                    headers: authHeaders(),
                    body: formData
                });
                if (!upload.ok) throw new Error(await responseMessage(upload, 'Không thể tải ảnh lên.'));
                const asset = await upload.json();
                imageUrl = asset.asset?.secureUrl || asset.secureUrl;
                imagePublicId = asset.asset?.publicId || asset.publicId;
            }

            const categoryGroup = document.getElementById('p_categoryGroup').value;
            const editing = Boolean(form.dataset.editId);
            const branchId = Number(document.getElementById('p_branchId').value) || (currentUser?.branchId ? currentUser.branchId : 1);

            if (categoryGroup === 'puppy') {
                const dogCategory = categoriesList.find(c => c.name?.includes('Chó')) || categoriesList[0];
                const puppyPayload = {
                    listingTitle: document.getElementById('p_name').value.trim(),
                    description: document.getElementById('p_description').value.trim(),
                    pricePerPuppyVnd: Number(document.getElementById('p_price').value),
                    stock: Number(document.getElementById('p_stock').value),
                    branchId: branchId,
                    categoryId: dogCategory ? dogCategory.id : 1,
                    breedCode: document.getElementById('p_breed').value.trim() || 'BREED_UNKNOWN',
                    breedType: 'PUREBRED',
                    lifeStage: 'PUPPY',
                    ageMonths: Number(document.getElementById('p_age')?.value) || 3,
                    healthStatus: document.getElementById('p_healthStatus')?.value.trim() || 'Khỏe mạnh',
                    careInstructions: document.getElementById('p_careInstructions')?.value.trim() || 'Theo dõi dinh dưỡng',
                    status: 'AVAILABLE',
                    imageUrl, imagePublicId
                };

                const url = editing ? `/api/puppy-listings/${form.dataset.editId}` : '/api/puppy-listings';
                const response = await fetch(url, {
                    method: editing ? 'PUT' : 'POST',
                    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
                    body: JSON.stringify(puppyPayload)
                });
                if (!response.ok) throw new Error(await responseMessage(response, 'Không thể lưu chó con.'));
            } else {
                const foodCategory = categoriesList.find(c => c.name?.includes('Thức ăn') || c.name?.includes('Phụ kiện')) || categoriesList[1] || categoriesList[0];
                const productPayload = {
                    name: document.getElementById('p_name').value.trim(),
                    description: document.getElementById('p_description').value.trim(),
                    price: Number(document.getElementById('p_price').value),
                    stock: Number(document.getElementById('p_stock').value),
                    branchId: branchId,
                    categoryId: foodCategory ? foodCategory.id : 2,
                    imageUrl, imagePublicId
                };

                const url = editing ? `/api/products/${form.dataset.editId}` : '/api/products';
                const response = await fetch(url, {
                    method: editing ? 'PUT' : 'POST',
                    headers: { ...authHeaders(), 'Content-Type': 'application/json' },
                    body: JSON.stringify(productPayload)
                });
                if (!response.ok) throw new Error(await responseMessage(response, 'Không thể lưu sản phẩm.'));
            }

            document.getElementById('addProductModal').classList.remove('active');
            await loadAllStoreItems();
        } catch (error) {
            status.textContent = error.message;
        } finally {
            saveButton.disabled = false;
        }
    }

    async function responseMessage(response, fallback) {
        try { return (await response.json()).message || fallback; } catch (_) { return fallback; }
    }

    function formatCurrency(value) { return `${new Intl.NumberFormat('vi-VN').format(value || 0)} VND`; }
    function escapeHtml(value) { const node = document.createElement('span'); node.textContent = value || ''; return node.innerHTML; }
    function escapeAttribute(value) { return String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;'); }
})();
