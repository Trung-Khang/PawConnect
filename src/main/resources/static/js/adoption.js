(function () {
    
    let currentAdoptionPage = 1;
    const ADOPTION_PAGE_SIZE = 6;

    function renderAdoptionPagination(totalPages) {
        const container = document.getElementById('adoptionPagination');
        if (!container) return;
        if (totalPages <= 1) {
            container.innerHTML = '';
            return;
        }

        let html = `
            <button class="paw-pagination-btn" ${currentAdoptionPage <= 1 ? 'disabled' : ''} onclick="window.changeAdoptionPage(${currentAdoptionPage - 1})">
                <i class="fas fa-chevron-left"></i> Trang trước
            </button>
        `;

        for (let p = 1; p <= totalPages; p++) {
            html += `
                <button class="paw-pagination-btn ${p === currentAdoptionPage ? 'active' : ''}" onclick="window.changeAdoptionPage(${p})">
                    ${p}
                </button>
            `;
        }

        html += `
            <button class="paw-pagination-btn" ${currentAdoptionPage >= totalPages ? 'disabled' : ''} onclick="window.changeAdoptionPage(${currentAdoptionPage + 1})">
                Trang sau <i class="fas fa-chevron-right"></i>
            </button>
        `;
        container.innerHTML = html;
    }

    window.changeAdoptionPage = function(page) {
        currentAdoptionPage = page;
        renderList();
        document.getElementById('adoption-list-view')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    };

const state = { posts: [], branches: [], user: null, currentPost: null, managedApplicationsPoll: null };
    const path = window.location.pathname;
    const ACCESS_TOKEN_KEY = 'pawconnect.accessToken';
    const REFRESH_TOKEN_KEY = 'pawconnect.refreshToken';
    const LEGACY_ACCESS_TOKEN_KEYS = ['accessToken', 'token'];
    const LEGACY_REFRESH_TOKEN_KEYS = ['refreshToken'];
    let accessToken = readAndMigrateToken(ACCESS_TOKEN_KEY, LEGACY_ACCESS_TOKEN_KEYS);
    let refreshToken = readAndMigrateToken(REFRESH_TOKEN_KEY, LEGACY_REFRESH_TOKEN_KEYS);

    const views = {
        list: document.getElementById('adoption-list-view'),
        detail: document.getElementById('adoption-detail-view'),
        mine: document.getElementById('my-applications-view'),
        manage: document.getElementById('manage-view')
    };

    function readAndMigrateToken(canonicalKey, legacyKeys) {
        const canonicalValue = localStorage.getItem(canonicalKey);
        if (canonicalValue) return canonicalValue;

        const legacyValue = [...legacyKeys]
            .map((key) => localStorage.getItem(key) || sessionStorage.getItem(key))
            .find(Boolean);
        if (!legacyValue) return null;

        localStorage.setItem(canonicalKey, legacyValue);
        [...legacyKeys, canonicalKey].forEach((key) => {
            if (key !== canonicalKey) localStorage.removeItem(key);
            sessionStorage.removeItem(key);
        });
        return legacyValue;
    }

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
        document.getElementById('adoption-search').addEventListener('input', () => { currentAdoptionPage = 1; renderList(); });
        const branchFilter = document.getElementById('adoption-branch-filter');
        if (branchFilter) branchFilter.addEventListener('change', () => { currentAdoptionPage = 1; renderList(); });
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
        const accountStatus = document.getElementById('account-status');
        if (accountStatus) {
            accountStatus.textContent = role === 'ADMIN'
                ? 'Quản trị viên'
                : role === 'BRANCH_MANAGER'
                    ? supportDisplayName(state.user?.fullName) || 'Nhân viên chi nhánh'
                    : 'Khách';
        }
        document.querySelectorAll('[data-customer-only]').forEach((link) => link.hidden = role !== 'CUSTOMER');
        document.querySelectorAll('[data-staff-only]').forEach((link) => link.hidden = !isStaff());
        document.querySelectorAll('[data-admin-only]').forEach((link) => link.hidden = role !== 'ADMIN');
    }

    function supportDisplayName(name) {
        return ({
            'Quản lý TP.HCM': 'Nhân viên TP.HCM',
            'Quản lý Hà Nội': 'Nhân viên Hà Nội',
            'Quản lý Đà Nẵng': 'Nhân viên Đà Nẵng'
        })[name] || name;
    }

    async function showList() {
        setPage('list', 'Tìm một người bạn đồng hành', 'Các hồ sơ đang cần một mái ấm tại PawConnect.');
        try {
            await loadBranches();
            populateListBranchFilter();
            state.posts = await request('/api/adoptions', { authenticated: false });
            renderList();
        } catch (error) {
            showError(error.message);
        }
    }

    function populateListBranchFilter() {
        const select = document.getElementById('adoption-branch-filter');
        if (!select || select.dataset.loaded) return;
        select.replaceChildren();
        const defaultOption = document.createElement('option');
        defaultOption.value = '';
        defaultOption.textContent = 'Tất cả chi nhánh';
        select.appendChild(defaultOption);
        state.branches.forEach((b) => {
            const opt = document.createElement('option');
            opt.value = String(b.id);
            opt.textContent = b.name;
            select.appendChild(opt);
        });
        select.dataset.loaded = 'true';
    }

    function renderList() {
        const container = document.getElementById('adoption-list');
        const query = document.getElementById('adoption-search').value.trim().toLocaleLowerCase('vi');
        const selectedBranchId = document.getElementById('adoption-branch-filter')?.value || '';
        const selectedBranchName = selectedBranchId
            ? (state.branches.find(b => String(b.id) === selectedBranchId)?.name || '')
            : '';
        const matches = state.posts.filter((post) => {
            const matchesQuery = !query || [post.dogName, post.dogProfile?.breed, post.title, post.description]
                .filter(Boolean).join(' ').toLocaleLowerCase('vi').includes(query);
            const matchesBranch = !selectedBranchId
                || String(post.dogProfile?.branchId) === selectedBranchId
                || (selectedBranchName && post.branchName && post.branchName === selectedBranchName);
            return matchesQuery && matchesBranch;
        });

        const totalMatches = matches.length;
        const totalPages = Math.ceil(totalMatches / ADOPTION_PAGE_SIZE) || 1;
        if (currentAdoptionPage > totalPages) currentAdoptionPage = totalPages;
        if (currentAdoptionPage < 1) currentAdoptionPage = 1;

        const start = (currentAdoptionPage - 1) * ADOPTION_PAGE_SIZE;
        const pagePosts = matches.slice(start, start + ADOPTION_PAGE_SIZE);

        document.getElementById('post-count').textContent = `${matches.length} hồ sơ đang cần được nhận nuôi`;
        container.replaceChildren();
        if (matches.length === 0) {
            renderEmpty(container);
            renderAdoptionPagination(0);
            return;
        }
        pagePosts.forEach((post) => container.appendChild(buildPostCard(post)));
        renderAdoptionPagination(totalPages);
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
            await refreshApplicationState();
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
            `Chi nhánh đăng: ${post.branchName || 'Chưa cập nhật'}`,
            `Mã bài: ${post.id}`,
            `Đăng lúc: ${formatDate(post.createdAt)}`
        ].forEach((value) => meta.append(textElement('span', value)));
        content.append(meta);
        detail.append(content);

        document.getElementById('application-form').hidden = state.user?.role !== 'CUSTOMER' || post.status !== 'AVAILABLE';
        document.getElementById('application-status-hint').hidden = true;
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
            return showError('Đăng nhập bằng tài khoản Admin hoặc Nhân viên chi nhánh để sử dụng trang này.');
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
        startManagedApplicationsPolling();
    }

    function startManagedApplicationsPolling() {
        if (state.managedApplicationsPoll) window.clearInterval(state.managedApplicationsPoll);
        state.managedApplicationsPoll = window.setInterval(loadManagedApplications, 10000);
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

            const header = document.createElement('div');
            header.className = 'application-status-header';
            header.append(statusElement(application.status));

            if (application.status === 'REJECTED') {
                const reasonTag = document.createElement('div');
                reasonTag.className = 'application-rejection-pill';
                reasonTag.innerHTML = `<i class="fas fa-circle-xmark"></i> <strong>Lý do từ chối:</strong> <span>${escapeHtml(application.rejectionReason || 'Hồ sơ chưa phù hợp vào thời điểm này')}</span>`;
                header.append(reasonTag);
            } else if (application.status === 'APPROVED' && !manageable) {
                const approvedTag = document.createElement('div');
                approvedTag.className = 'application-approved-pill';
                approvedTag.innerHTML = `<i class="fas fa-check-circle"></i> <span>Chi nhánh sẽ chủ động liên hệ bạn trong vài giờ tới để sắp xếp đón bé.</span>`;
                header.append(approvedTag);
            }
            row.append(header);

            if (manageable) row.append(textElement('strong', application.applicantName || 'Khách hàng'));
            row.append(textElement('p', application.message));
            const details = document.createElement('div');
            details.className = 'application-actions';
            const postLink = document.createElement('a');
            postLink.className = 'secondary-button';
            postLink.href = `/adoptions/${application.adoptionPostId}`;
            postLink.innerHTML = `<i class="fas fa-file-lines"></i> <span>Xem bài nhận nuôi #${application.adoptionPostId}</span>`;
            details.append(postLink);
            row.append(textElement('small', `${application.status === 'PENDING' ? 'Gửi lúc' : 'Cập nhật lúc'}: ${formatDate(application.status === 'PENDING' ? application.createdAt : application.updatedAt)}`));
            if (manageable && application.status === 'PENDING') {
                details.append(actionButton('Duyệt đơn', () => updateApplication(application.id, 'approve'), 'approve-button', 'fas fa-check'));
                details.append(actionButton('Từ chối', () => updateApplication(application.id, 'reject'), 'secondary-button', 'fas fa-xmark'));
            }
            row.append(details);
            container.append(row);
        });
    }

    async function submitApplication(event) {
        event.preventDefault();
        const form = event.currentTarget;
        const submitButton = form.querySelector('button[type="submit"]');
        if (submitButton.disabled) return;
        submitButton.disabled = true;
        try {
            await request(`/api/adoptions/${state.currentPost.id}/apply`, {
                method: 'POST', body: { message: form.message.value }
            });
            form.reset();
            showNotice('Đơn đăng ký đã được gửi. Nhân viên sẽ xem xét và phản hồi sớm nhất có thể.');
            await refreshApplicationState();
        } catch (error) {
            showError(error.message);
            submitButton.disabled = false;
        }
    }

    async function refreshApplicationState() {
        const form = document.getElementById('application-form');
        const hint = document.getElementById('application-status-hint');
        if (state.user?.role !== 'CUSTOMER' || !state.currentPost) {
            hint.hidden = true;
            return;
        }
        try {
            const applications = await request('/api/adoptions/applications/my');
            const existing = applications.find((application) => Number(application.adoptionPostId) === Number(state.currentPost.id));
            if (!existing) return;
            form.hidden = true;
            hint.hidden = false;
            hint.textContent = existing.status === 'PENDING'
                ? 'Bạn đã gửi đơn đăng ký cho bài này. Đơn đang chờ nhân viên chi nhánh xử lý.'
                : existing.status === 'APPROVED'
                    ? 'Đơn đăng ký của bạn đã được duyệt! Nhân viên sẽ chủ động liên hệ với bạn trong vài giờ sắp tới để sắp xếp thời gian rước bé.'
                    : `Đơn đăng ký trước đó đã được từ chối${existing.rejectionReason ? ': ' + existing.rejectionReason : '.'}`;
        } catch (_) {
            // Keep the form available if the status lookup temporarily fails.
        }
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

    function promptRejectModal({ title, subtitle, defaultReason, quickTags }) {
        return new Promise((resolve) => {
            const overlay = document.createElement('div');
            overlay.className = 'frosted-modal-overlay';

            const card = document.createElement('div');
            card.className = 'frosted-glass-card';

            const tagsHtml = (quickTags || []).map(tag =>
                `<button type="button" class="frosted-tag-btn">${escapeHtml(tag)}</button>`
            ).join('');

            card.innerHTML = `
                <button type="button" class="frosted-modal-close" aria-label="Đóng"><i class="fas fa-xmark"></i></button>
                <div class="frosted-modal-header">
                    <div class="frosted-modal-icon">
                        <i class="fas fa-file-circle-xmark"></i>
                    </div>
                    <div>
                        <h3 class="frosted-modal-title">${escapeHtml(title || 'Từ chối đơn')}</h3>
                        <p class="frosted-modal-desc">${escapeHtml(subtitle || 'Nhập lý do gửi đến khách hàng')}</p>
                    </div>
                </div>
                ${tagsHtml ? `<div class="frosted-quick-tags">${tagsHtml}</div>` : ''}
                <textarea class="frosted-textarea" maxlength="255" placeholder="Nhập lý do gửi đến khách hàng (ngắn gọn, tối đa 255 ký tự)...">${escapeHtml(defaultReason || '')}</textarea>
                <div style="font-size: 0.78rem; color: #64748b; margin-top: -0.4rem; margin-bottom: 0.8rem; text-align: right;"><i class="fas fa-keyboard"></i> Tối đa 255 ký tự</div>
                <div class="frosted-modal-actions">
                    <button type="button" class="frosted-btn-cancel">Hủy bỏ</button>
                    <button type="button" class="frosted-btn-confirm">Xác nhận từ chối</button>
                </div>
            `;

            overlay.appendChild(card);
            document.body.appendChild(overlay);

            requestAnimationFrame(() => overlay.classList.add('active'));

            const textarea = card.querySelector('.frosted-textarea');
            textarea.focus();
            textarea.select();

            card.querySelectorAll('.frosted-tag-btn').forEach(btn => {
                btn.addEventListener('click', () => {
                    textarea.value = btn.textContent;
                    textarea.focus();
                });
            });

            function cleanup(result) {
                overlay.classList.remove('active');
                setTimeout(() => {
                    overlay.remove();
                    resolve(result);
                }, 250);
            }

            card.querySelector('.frosted-modal-close').addEventListener('click', () => cleanup(null));
            card.querySelector('.frosted-btn-cancel').addEventListener('click', () => cleanup(null));
            overlay.addEventListener('click', (e) => {
                if (e.target === overlay) cleanup(null);
            });

            card.querySelector('.frosted-btn-confirm').addEventListener('click', () => {
                const val = textarea.value.trim();
                if (!val) {
                    alert('Vui lòng nhập lý do từ chối.');
                    textarea.focus();
                    return;
                }
                cleanup(val);
            });
        });
    }

    function escapeHtml(text) {
        if (!text) return '';
        const d = document.createElement('div');
        d.textContent = text;
        return d.innerHTML;
    }

    async function updateApplication(id, action) {
        try {
            let reason = null;
            if (action === 'reject') {
                reason = await promptRejectModal({
                    title: 'Từ chối đơn nhận nuôi',
                    subtitle: 'Nhập lý do từ chối để hệ thống phản hồi và thông báo tới người đăng ký.',
                    defaultReason: 'Hồ sơ chưa phù hợp vào thời điểm này',
                    quickTags: [
                        'Hồ sơ chưa phù hợp vào thời điểm này',
                        'Chi nhánh đã duyệt cho gia đình khác',
                        'Chưa đủ điều kiện không gian chăm sóc bé',
                        'Khách hàng đổi ý không muốn nhận nuôi nữa'
                    ]
                });
                if (reason === null) return;
            }
            await request(`/api/adoptions/applications/${id}/${action}`, {
                method: 'PUT',
                body: action === 'reject' ? { reason } : undefined
            });
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
        if (authenticated && response.status === 401) handleSessionExpired();
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
                handleSessionExpired();
                return false;
            }
            const auth = await response.json();
            if (!auth.accessToken || !auth.refreshToken) {
                handleSessionExpired();
                return false;
            }
            accessToken = auth.accessToken;
            refreshToken = auth.refreshToken;
            localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
            localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
            return true;
        } catch (_) {
            handleSessionExpired();
            return false;
        }
    }

    function clearStoredAuth() {
        accessToken = null;
        refreshToken = null;
        [ACCESS_TOKEN_KEY, ...LEGACY_ACCESS_TOKEN_KEYS, REFRESH_TOKEN_KEY, ...LEGACY_REFRESH_TOKEN_KEYS]
            .forEach((key) => {
                localStorage.removeItem(key);
                sessionStorage.removeItem(key);
            });
    }

    function handleSessionExpired() {
        clearStoredAuth();
        if (window.location.pathname !== '/adoptions') {
            window.location.assign('/adoptions?session=expired');
        }
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
        window.PawConnectChat?.openAdoption(Number(document.getElementById('open-adoption-chat').dataset.adoptionPostId));
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
        const metaRow = document.createElement('div');
        metaRow.className = 'post-card-meta-row';
        const dogMeta = textElement('p', [post.dogName, post.dogProfile?.breed].filter(Boolean).join(' · ') || 'Hồ sơ chưa cập nhật');
        dogMeta.className = 'post-card-dog-meta';
        metaRow.append(dogMeta);
        if (post.branchName) {
            const branchSpan = document.createElement('span');
            branchSpan.className = 'post-card-branch';
            branchSpan.title = post.branchName;
            const branchIcon = document.createElement('i');
            branchIcon.className = 'fas fa-location-dot';
            const branchText = document.createElement('span');
            branchText.textContent = post.branchName;
            branchSpan.append(branchIcon, branchText);
            metaRow.append(branchSpan);
        }
        content.append(metaRow);
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

    function actionButton(label, listener, className, iconClass) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = className || '';
        if (iconClass) {
            button.innerHTML = `<i class="${iconClass}"></i> <span>${escapeHtml(label)}</span>`;
        } else {
            button.textContent = label;
        }
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


