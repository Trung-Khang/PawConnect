(function () {
    const state = { posts: [], branches: [], user: null, currentPost: null };
    const path = window.location.pathname;
    let accessToken = localStorage.getItem('pawconnect.accessToken')
        || localStorage.getItem('accessToken')
        || localStorage.getItem('token')
        || sessionStorage.getItem('pawconnect.accessToken')
        || sessionStorage.getItem('accessToken')
        || sessionStorage.getItem('token');
    let refreshToken = localStorage.getItem('pawconnect.refreshToken')
        || localStorage.getItem('refreshToken')
        || sessionStorage.getItem('pawconnect.refreshToken')
        || sessionStorage.getItem('refreshToken');

    const views = {
        list: document.getElementById('adoption-list-view'),
        detail: document.getElementById('adoption-detail-view'),
        mine: document.getElementById('my-applications-view'),
        manage: document.getElementById('manage-view')
    };

    document.addEventListener('DOMContentLoaded', initialise);

    async function initialise() {
        await loadCurrentUser();
        updateNavigation();
        bindEvents();
        if (path === '/adoptions/my-applications') return showMyApplications();
        if (path === '/adoptions/manage') return showManagement();
        const id = path.match(/^\/adoptions\/(\d+)$/)?.[1];
        if (id) return showDetail(id);
        return showList();
    }

    function bindEvents() {
        document.getElementById('adoption-search').addEventListener('input', renderList);
        document.getElementById('application-form').addEventListener('submit', submitApplication);
        document.getElementById('post-edit-form').addEventListener('submit', updatePost);
        document.getElementById('create-adoption-form').addEventListener('submit', createAdoption);
        document.getElementById('reload-managed').addEventListener('click', loadManagedApplications);
        document.getElementById('reload-managed-posts').addEventListener('click', loadManagedPosts);
        document.getElementById('delete-dog-image').addEventListener('click', deleteDogImage);
        document.getElementById('delete-adoption-post').addEventListener('click', deleteAdoptionPost);
        document.getElementById('open-adoption-chat').addEventListener('click', openAdoptionChat);
    }

    async function loadCurrentUser() {
        if (!accessToken && !refreshToken) return;
        try {
            state.user = await request('/api/auth/me');
        } catch (_) {
            state.user = null;
        }
    }

    function updateNavigation() {
        const role = state.user?.role;
        document.getElementById('account-status').textContent = state.user ? state.user.fullName : 'Khách';
        document.querySelectorAll('[data-customer-only]').forEach((link) => link.hidden = role !== 'CUSTOMER');
        document.querySelectorAll('[data-staff-only]').forEach((link) => link.hidden = !isStaff());
    }

    async function showList() {
        setPage('list', 'Tìm một người bạn đồng hành', 'Các hồ sơ đang cần một mái ấm tại PawConnect.');
        try {
            state.posts = await request('/api/adoptions', { authenticated: false });
            renderList();
        } catch (error) {
            showError(error.message);
        }
    }

    function renderList() {
        const container = document.getElementById('adoption-list');
        const query = document.getElementById('adoption-search').value.trim().toLocaleLowerCase('vi');
        const matches = state.posts.filter((post) => [post.dogName, post.dogProfile?.breed, post.title, post.description]
            .filter(Boolean).join(' ').toLocaleLowerCase('vi').includes(query));
        document.getElementById('post-count').textContent = `${matches.length} hồ sơ đang cần được nhận nuôi`;
        container.replaceChildren();
        if (matches.length === 0) return renderEmpty(container);
        matches.forEach((post) => container.appendChild(buildPostCard(post)));
    }

    async function showDetail(id) {
        setPage('detail', 'Thông tin nhận nuôi', 'Tìm hiểu hồ sơ và gửi đơn khi bạn đã sẵn sàng.');
        try {
            try {
                state.currentPost = await request(`/api/adoptions/${id}`, { authenticated: false });
            } catch (error) {
                if (!isStaff()) throw error;
                state.currentPost = await request(`/api/adoptions/manage/posts/${id}`);
            }
            if (isStaff()) await loadBranches();
            renderDetail(state.currentPost);
        } catch (error) {
            showError(error.message);
        }
    }

    function renderDetail(post) {
        const dog = post.dogProfile || {};
        const detail = document.getElementById('adoption-detail');
        detail.replaceChildren(imageElement(post.imageUrl || dog.imageUrl, post.dogName));
        const content = document.createElement('div');
        content.className = 'detail-content';
        content.append(statusElement(post.status));
        content.append(textElement('h2', post.title));
        content.append(textElement('p', post.description));
        const meta = document.createElement('div');
        meta.className = 'meta-grid';
        [
            `Tên: ${post.dogName || 'Chưa cập nhật'}`,
            `Giống: ${dog.breed || 'Chưa cập nhật'}`,
            `Kích thước: ${sizeLabel(dog.size)}`,
            `Tuổi: ${Number.isInteger(dog.ageMonths) ? `${dog.ageMonths} tháng` : 'Chưa cập nhật'}`,
            `Cân nặng: ${dog.weightKg ? `${dog.weightKg} kg` : 'Chưa cập nhật'}`,
            `Giới tính: ${dog.gender === 'MALE' ? 'Đực' : dog.gender === 'FEMALE' ? 'Cái' : 'Chưa cập nhật'}`,
            `Tiêm chủng: ${vaccinationLabel(dog.vaccinationStatus)}`,
            `Ghi chú sức khỏe: ${post.healthNote || 'Chưa cập nhật'}`,
            `Mã bài: ${post.id}`,
            `Đăng lúc: ${formatDate(post.createdAt)}`
        ].forEach((value) => meta.append(textElement('span', value)));
        content.append(meta);
        detail.append(content);

        document.getElementById('application-form').hidden = state.user?.role !== 'CUSTOMER';
        document.getElementById('application-login-hint').hidden = Boolean(state.user) || state.user?.role === 'CUSTOMER';
        const chatEntry = document.getElementById('consultation-chat-entry');
        const chatButton = document.getElementById('open-adoption-chat');
        chatEntry.hidden = isStaff() || post.status !== 'AVAILABLE';
        chatButton.dataset.adoptionPostId = String(post.id);
        chatButton.dataset.dogProfileId = String(post.dogProfileId || dog.id || '');
        const editPanel = document.getElementById('post-edit-panel');
        editPanel.hidden = !isStaff();
        if (isStaff()) {
            const form = document.getElementById('post-edit-form');
            form.dogName.value = dog.name || '';
            form.dogBreed.value = dog.breed || '';
            form.dogSize.value = dog.size || 'SMALL';
            form.dogAgeMonths.value = dog.ageMonths ?? 0;
            form.dogWeightKg.value = dog.weightKg ?? '';
            form.dogGender.value = dog.gender || 'MALE';
            form.dogVaccinationStatus.value = dog.vaccinationStatus || 'NOT_VACCINATED';
            form.dogDescription.value = dog.description || '';
            populateBranchSelect(form.dogBranchId, dog.branchId);
            form.title.value = post.title || '';
            form.description.value = post.description || '';
            form.healthNote.value = post.healthNote || '';
            form.status.value = post.status || 'AVAILABLE';
            document.getElementById('delete-dog-image').disabled = !dog.imageUrl;
        }
    }

    async function showMyApplications() {
        setPage('mine', 'Đơn nhận nuôi của tôi', 'Theo dõi trạng thái các đơn bạn đã gửi.');
        if (state.user?.role !== 'CUSTOMER') return showError('Trang này chỉ dành cho tài khoản khách hàng.');
        try {
            const applications = await request('/api/adoptions/applications/my');
            renderApplications(document.getElementById('my-applications'), applications, false);
        } catch (error) {
            showError(error.message);
        }
    }

    async function showManagement() {
        setPage('manage', 'Quản lý nhận nuôi', 'Tạo bài nhận nuôi và xử lý đơn thuộc phạm vi được phân quyền.');
        if (!isStaff()) {
            document.querySelector('#manage-view .manage-layout').hidden = true;
            return showError('Đăng nhập bằng tài khoản Admin hoặc Quản lý chi nhánh để sử dụng trang này.');
        }
        document.querySelector('#manage-view .manage-layout').hidden = false;
        try {
            await loadBranches();
            populateBranchSelect(document.getElementById('create-adoption-form').branchId);
        } catch (error) {
            return showError(error.message);
        }
        showStoredNotice();
        await loadManagedApplications();
        await loadManagedPosts();
    }

    async function loadManagedApplications() {
        const container = document.getElementById('managed-applications');
        try {
            const applications = await request('/api/adoptions/applications/manage');
            renderApplications(container, applications, true);
        } catch (error) {
            showError(error.message);
        }
    }

    async function loadManagedPosts() {
        const container = document.getElementById('managed-posts');
        container.replaceChildren(textElement('p', 'Đang tải danh sách bài đăng...'));
        try {
            const posts = await request('/api/adoptions/manage/posts');
            container.replaceChildren();
            if (posts.length === 0) return renderEmpty(container);
            posts.forEach((post) => container.append(renderManagedPost(post)));
        } catch (error) {
            container.replaceChildren(textElement('p', `Không thể tải bài đã đăng: ${error.message}`));
            showError(error.message);
        }
    }

    function renderManagedPost(post) {
        const row = document.createElement('article');
        row.className = 'managed-post-row';
        const content = document.createElement('div');
        content.append(statusElement(post.status));
        content.append(textElement('h3', post.title));
        content.append(textElement('p', [post.dogName, post.dogProfile?.breed].filter(Boolean).join(' · ')));
        content.append(textElement('small', `Đăng lúc ${formatDate(post.createdAt)}`));
        const link = document.createElement('a');
        link.className = 'secondary-button';
        link.href = `/adoptions/${post.id}`;
        link.textContent = 'Xem và chỉnh sửa';
        row.append(content, link);
        return row;
    }

    function renderApplications(container, applications, manageable) {
        container.replaceChildren();
        if (applications.length === 0) return renderEmpty(container);
        applications.forEach((application) => {
            const row = document.createElement('article');
            row.className = 'application-row';
            row.append(statusElement(application.status));
            row.append(textElement('p', application.message));
            row.append(textElement('small', `Bài nhận nuôi #${application.adoptionPostId} · ${formatDate(application.createdAt)}`));
            if (manageable && application.status === 'PENDING') {
                const actions = document.createElement('div');
                actions.className = 'application-actions';
                actions.append(actionButton('Duyệt đơn', () => updateApplication(application.id, 'approve')));
                actions.append(actionButton('Từ chối', () => updateApplication(application.id, 'reject'), true));
                row.append(actions);
            }
            container.append(row);
        });
    }

    async function submitApplication(event) {
        event.preventDefault();
        try {
            await request(`/api/adoptions/${state.currentPost.id}/apply`, {
                method: 'POST', body: { message: event.currentTarget.message.value }
            });
            event.currentTarget.reset();
            showNotice('Đơn đăng ký đã được gửi.');
        } catch (error) { showError(error.message); }
    }

    async function updatePost(event) {
        event.preventDefault();
        const form = event.currentTarget;
        try {
            let updated = await request(`/api/adoptions/${state.currentPost.id}`, {
                method: 'PUT', body: {
                    title: form.title.value, description: form.description.value, healthNote: form.healthNote.value,
                    imageUrl: state.currentPost.imageUrl, imagePublicId: state.currentPost.imagePublicId, status: form.status.value,
                    dogProfile: {
                        name: form.dogName.value, breed: form.dogBreed.value, size: form.dogSize.value,
                        ageMonths: Number(form.dogAgeMonths.value),
                        weightKg: form.dogWeightKg.value ? Number(form.dogWeightKg.value) : null,
                        gender: form.dogGender.value, vaccinationStatus: form.dogVaccinationStatus.value,
                        imageUrl: state.currentPost.dogProfile?.imageUrl || null,
                        imagePublicId: state.currentPost.dogProfile?.imagePublicId || null,
                        description: form.dogDescription.value || null, branchId: Number(form.dogBranchId.value)
                    }
                }
            });
            const dogImage = form.dogImage.files[0];
            const image = form.postImage.files[0];
            if (dogImage) {
                const dog = await uploadImage(`/api/adoptions/dogs/${state.currentPost.dogProfileId}/image`, dogImage);
                updated.dogProfile = dog;
                updated.dogName = dog.name;
            }
            if (image) updated = await uploadImage(`/api/adoptions/posts/${state.currentPost.id}/image`, image);
            if (updated.status === 'CLOSED') {
                redirectToManagement('Bài nhận nuôi đã được đóng.');
                return;
            }
            state.currentPost = updated;
            renderDetail(updated);
            showNotice('Bài nhận nuôi đã được cập nhật.');
        } catch (error) { showError(error.message); }
    }

    async function createAdoption(event) {
        event.preventDefault();
        const form = event.currentTarget;
        const field = (name) => form.elements[name].value.trim();
        try {
            const post = await request('/api/adoptions', {
                method: 'POST', body: {
                    dogProfile: {
                        name: field('name'), breed: field('breed'), size: field('size'), ageMonths: Number(field('ageMonths')),
                        weightKg: field('weightKg') ? Number(field('weightKg')) : null, gender: field('gender'),
                        vaccinationStatus: field('vaccinationStatus'), imageUrl: null, imagePublicId: null,
                        description: field('dogDescription') || null, branchId: Number(field('branchId'))
                    },
                    title: field('title'), description: field('description'), healthNote: field('healthNote') || null,
                    imageUrl: null, imagePublicId: null
                }
            });
            const dogImage = form.dogImage.files[0];
            const postImage = form.postImage.files[0];
            if (dogImage) await uploadImage(`/api/adoptions/dogs/${post.dogProfileId}/image`, dogImage);
            if (postImage) await uploadImage(`/api/adoptions/posts/${post.id}/image`, postImage);
            form.reset();
            showNotice('Bài nhận nuôi đã được tạo.');
            window.location.assign(`/adoptions/${post.id}`);
        } catch (error) { showError(error.message); }
    }

    async function updateApplication(id, action) {
        try {
            await request(`/api/adoptions/applications/${id}/${action}`, { method: 'PUT' });
            showNotice(action === 'approve' ? 'Đơn đã được duyệt và bài đã đóng.' : 'Đơn đã được từ chối.');
            await loadManagedApplications();
        } catch (error) { showError(error.message); }
    }

    async function deleteDogImage() {
        if (!state.currentPost?.dogProfile?.imageUrl) return;
        try {
            await request(`/api/adoptions/dogs/${state.currentPost.dogProfileId}/image`, { method: 'DELETE' });
            state.currentPost.dogProfile.imageUrl = null;
            state.currentPost.dogProfile.imagePublicId = null;
            renderDetail(state.currentPost);
            showNotice('Ảnh hồ sơ chó đã được xóa.');
        } catch (error) { showError(error.message); }
    }

    async function deleteAdoptionPost() {
        if (!state.currentPost) return;
        const confirmed = window.confirm(
            'Xóa vĩnh viễn bài đăng này? Bài không cần đóng trước, nhưng chỉ bài chưa có đơn nhận nuôi mới có thể bị xóa. Hồ sơ chó sẽ được giữ lại.'
        );
        if (!confirmed) return;
        try {
            await request(`/api/adoptions/${state.currentPost.id}`, { method: 'DELETE' });
            redirectToManagement('Bài nhận nuôi đã được xóa.');
        } catch (error) { showError(error.message); }
    }

    async function loadBranches() {
        if (state.branches.length > 0) return state.branches;
        state.branches = await request('/api/branches', { authenticated: false });
        return state.branches;
    }

    function populateBranchSelect(select, selectedId) {
        select.replaceChildren();
        state.branches.forEach((branch) => {
            const option = document.createElement('option');
            option.value = String(branch.id);
            option.textContent = branch.name;
            option.selected = Number(selectedId) === Number(branch.id);
            select.append(option);
        });
    }

    async function request(url, options = {}) {
        const authenticated = options.authenticated !== false;
        const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}) };
        if (authenticated && accessToken) headers.Authorization = `Bearer ${accessToken}`;
        const body = options.formData || (options.body ? JSON.stringify(options.body) : undefined);
        let response = await fetch(url, { method: options.method || 'GET', headers, body });
        if (authenticated && response.status === 401 && refreshToken) {
            const refreshed = await refreshAccessToken();
            if (refreshed) {
                headers.Authorization = `Bearer ${accessToken}`;
                response = await fetch(url, { method: options.method || 'GET', headers, body });
            }
        }
        if (authenticated && response.status === 401) clearStoredAuth();
        if (!response.ok) throw new Error(await responseMessage(response));
        return response.status === 204 ? null : response.json();
    }

    async function refreshAccessToken() {
        try {
            const response = await fetch('/api/auth/refresh-token', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refreshToken })
            });
            if (!response.ok) {
                clearStoredAuth();
                return false;
            }
            const auth = await response.json();
            if (!auth.accessToken || !auth.refreshToken) {
                clearStoredAuth();
                return false;
            }
            accessToken = auth.accessToken;
            refreshToken = auth.refreshToken;
            localStorage.setItem('pawconnect.accessToken', accessToken);
            localStorage.setItem('pawconnect.refreshToken', refreshToken);
            return true;
        } catch (_) {
            return false;
        }
    }

    function clearStoredAuth() {
        accessToken = null;
        refreshToken = null;
        ['pawconnect.accessToken', 'accessToken', 'token', 'pawconnect.refreshToken', 'refreshToken']
            .forEach((key) => {
                localStorage.removeItem(key);
                sessionStorage.removeItem(key);
            });
    }

    async function uploadImage(url, file) {
        const formData = new FormData();
        formData.append('file', file);
        return request(url, { method: 'POST', formData });
    }

    function openAdoptionChat() {
        if (!state.user) {
            showError('Đăng nhập bằng tài khoản khách hàng để chat tư vấn.');
            return;
        }
        if (state.user.role !== 'CUSTOMER') {
            showError('Chat tư vấn dành cho tài khoản khách hàng.');
            return;
        }
        showNotice('Chat tư vấn cho hồ sơ này sẽ được tích hợp ở Giai đoạn 7.');
    }

    function redirectToManagement(message) {
        sessionStorage.setItem('pawconnect.adoptionNotice', message);
        window.location.assign('/adoptions/manage');
    }

    function showStoredNotice() {
        const message = sessionStorage.getItem('pawconnect.adoptionNotice');
        if (!message) return;
        sessionStorage.removeItem('pawconnect.adoptionNotice');
        showNotice(message);
    }

    async function responseMessage(response) {
        try {
            const body = await response.json();
            return body.message || body.error || `Yêu cầu không thành công (${response.status}).`;
        } catch (_) { return `Yêu cầu không thành công (${response.status}).`; }
    }

    function setPage(view, title, subtitle) {
        document.body.dataset.view = view;
        Object.entries(views).forEach(([name, element]) => element.hidden = name !== view);
        document.getElementById('page-title').textContent = title;
        document.getElementById('page-subtitle').textContent = subtitle;
    }

    function buildPostCard(post) {
        const card = document.createElement('article');
        card.className = 'post-card';
        const detailHref = `/adoptions/${post.id}`;
        const imageLink = document.createElement('a');
        imageLink.className = 'post-card-media-link';
        imageLink.href = detailHref;
        imageLink.setAttribute('aria-label', `Xem hồ sơ của ${post.dogName || 'chó nhận nuôi'}`);
        imageLink.append(imageElement(post.imageUrl || post.dogProfile?.imageUrl, post.dogName));
        card.append(imageLink);
        const content = document.createElement('div');
        content.className = 'post-card-content';
        content.append(statusElement(post.status));
        const title = textElement('h2', '');
        const titleLink = document.createElement('a');
        titleLink.className = 'post-card-title-link';
        titleLink.href = detailHref;
        titleLink.append(textElement('span', post.title));
        title.append(titleLink);
        content.append(title);
        content.append(textElement('p', [post.dogName, post.dogProfile?.breed].filter(Boolean).join(' · ') || 'Hồ sơ chưa cập nhật'));
        const link = document.createElement('a');
        link.href = detailHref;
        link.textContent = 'Xem hồ sơ';
        content.append(link);
        card.append(content);
        return card;
    }

    function imageElement(url, name) {
        if (url && /^https:\/\//.test(url)) {
            const image = document.createElement('img');
            image.className = 'dog-image'; image.src = url; image.alt = name || 'Chó chờ nhận nuôi';
            image.addEventListener('error', () => image.replaceWith(initialElement(name)));
            return image;
        }
        return initialElement(name);
    }

    function initialElement(name) {
        const empty = document.createElement('div');
        empty.className = 'dog-image-empty'; empty.setAttribute('aria-label', 'Chưa có ảnh');
        empty.textContent = (name || 'P').trim().charAt(0).toUpperCase();
        return empty;
    }

    function statusElement(status) {
        const badge = document.createElement('span');
        badge.className = `status ${String(status || '').toLowerCase()}`;
        badge.textContent = ({ AVAILABLE: 'Đang cần được nhận nuôi', CLOSED: 'Đã đóng nhận nuôi', PENDING: 'Đang chờ', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối' })[status] || status;
        return badge;
    }

    function sizeLabel(size) { return ({ SMALL: 'Nhỏ', MEDIUM: 'Vừa', LARGE: 'Lớn' })[size] || 'Chưa cập nhật'; }
    function vaccinationLabel(status) { return ({ NOT_VACCINATED: 'Chưa tiêm', PARTIALLY_VACCINATED: 'Tiêm một phần', FULLY_VACCINATED: 'Tiêm đầy đủ' })[status] || 'Chưa cập nhật'; }

    function actionButton(label, listener, secondary) {
        const button = document.createElement('button');
        button.type = 'button'; button.textContent = label;
        if (secondary) button.className = 'secondary-button';
        button.addEventListener('click', listener);
        return button;
    }

    function textElement(tag, value) { const element = document.createElement(tag); element.textContent = value || ''; return element; }
    function formatDate(value) { return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Chưa cập nhật'; }
    function isStaff() { return ['ADMIN', 'BRANCH_MANAGER'].includes(state.user?.role); }
    function renderEmpty(container) { container.append(document.getElementById('empty-state-template').content.cloneNode(true)); }
    function showNotice(message) { const notice = document.getElementById('notice'); notice.hidden = false; notice.classList.remove('error'); notice.textContent = message; }
    function showError(message) { const notice = document.getElementById('notice'); notice.hidden = false; notice.classList.add('error'); notice.textContent = message; }
})();
