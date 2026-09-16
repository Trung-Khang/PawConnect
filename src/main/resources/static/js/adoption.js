(function () {
    const state = { posts: [], user: null, currentPost: null };
    const path = window.location.pathname;
    const token = localStorage.getItem('pawconnect.accessToken')
        || localStorage.getItem('accessToken')
        || sessionStorage.getItem('pawconnect.accessToken')
        || sessionStorage.getItem('accessToken');

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
    }

    async function loadCurrentUser() {
        if (!token) return;
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
        setPage('list', 'Tìm một người bạn đồng hành', 'Các hồ sơ đang sẵn sàng nhận nuôi tại PawConnect.');
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
        const matches = state.posts.filter((post) => [post.dogName, post.title, post.description]
            .filter(Boolean).join(' ').toLocaleLowerCase('vi').includes(query));
        document.getElementById('post-count').textContent = `${matches.length} bài đang nhận nuôi`;
        container.replaceChildren();
        if (matches.length === 0) return renderEmpty(container);
        matches.forEach((post) => container.appendChild(buildPostCard(post)));
    }

    async function showDetail(id) {
        setPage('detail', 'Thông tin nhận nuôi', 'Tìm hiểu hồ sơ và gửi đơn khi bạn đã sẵn sàng.');
        try {
            state.currentPost = await request(`/api/adoptions/${id}`, { authenticated: false });
            renderDetail(state.currentPost);
        } catch (error) {
            showError(error.message);
        }
    }

    function renderDetail(post) {
        const detail = document.getElementById('adoption-detail');
        detail.replaceChildren(imageElement(post.imageUrl, post.dogName));
        const content = document.createElement('div');
        content.className = 'detail-content';
        content.append(statusElement(post.status));
        content.append(textElement('h2', post.title));
        content.append(textElement('p', post.description));
        const meta = document.createElement('div');
        meta.className = 'meta-grid';
        [
            `Tên: ${post.dogName || 'Chưa cập nhật'}`,
            `Ghi chú sức khỏe: ${post.healthNote || 'Chưa cập nhật'}`,
            `Mã bài: ${post.id}`,
            `Đăng lúc: ${formatDate(post.createdAt)}`
        ].forEach((value) => meta.append(textElement('span', value)));
        content.append(meta);
        detail.append(content);

        document.getElementById('application-form').hidden = state.user?.role !== 'CUSTOMER';
        document.getElementById('application-login-hint').hidden = Boolean(state.user) || state.user?.role === 'CUSTOMER';
        const editPanel = document.getElementById('post-edit-panel');
        editPanel.hidden = !isStaff();
        if (isStaff()) {
            const form = document.getElementById('post-edit-form');
            form.title.value = post.title || '';
            form.description.value = post.description || '';
            form.healthNote.value = post.healthNote || '';
            form.status.value = post.status || 'AVAILABLE';
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
        if (!isStaff()) return showError('Trang này chỉ dành cho Admin hoặc Quản lý chi nhánh.');
        await loadManagedApplications();
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
            const updated = await request(`/api/adoptions/${state.currentPost.id}`, {
                method: 'PUT', body: {
                    title: form.title.value, description: form.description.value, healthNote: form.healthNote.value,
                    imageUrl: state.currentPost.imageUrl, imagePublicId: state.currentPost.imagePublicId, status: form.status.value
                }
            });
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

    async function request(url, options = {}) {
        const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}) };
        if (options.authenticated !== false && token) headers.Authorization = `Bearer ${token}`;
        const response = await fetch(url, { method: options.method || 'GET', headers, body: options.body ? JSON.stringify(options.body) : undefined });
        if (!response.ok) throw new Error(await responseMessage(response));
        return response.status === 204 ? null : response.json();
    }

    async function responseMessage(response) {
        try {
            const body = await response.json();
            return body.message || body.error || `Yêu cầu không thành công (${response.status}).`;
        } catch (_) { return `Yêu cầu không thành công (${response.status}).`; }
    }

    function setPage(view, title, subtitle) {
        Object.entries(views).forEach(([name, element]) => element.hidden = name !== view);
        document.getElementById('page-title').textContent = title;
        document.getElementById('page-subtitle').textContent = subtitle;
    }

    function buildPostCard(post) {
        const card = document.createElement('article');
        card.className = 'post-card';
        card.append(imageElement(post.imageUrl, post.dogName));
        const content = document.createElement('div');
        content.className = 'post-card-content';
        content.append(statusElement(post.status));
        content.append(textElement('h2', post.title));
        content.append(textElement('p', post.dogName || 'Hồ sơ chưa cập nhật tên'));
        const link = document.createElement('a');
        link.href = `/adoptions/${post.id}`;
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
        badge.textContent = ({ AVAILABLE: 'Đang nhận nuôi', CLOSED: 'Đã đóng', PENDING: 'Đang chờ', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối' })[status] || status;
        return badge;
    }

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
