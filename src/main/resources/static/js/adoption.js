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

const state = { posts: [], branches: [], user: null, currentPost: null, managedApplicationsPoll: null, managedApplications: [], applicationFilter: 'ALL', rejectionTargetApp: null };
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
        if (path === '/adoptions/my-applications' || path === '/adoption/my-applications') return showMyApplications();
        if (path === '/adoptions/manage' || path === '/adoption/manage') return showManagement();
        const id = path.match(/^\/adoptions?\/(\d+)$/)?.[1];
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
        document.querySelectorAll('[data-app-filter]').forEach((btn) => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('[data-app-filter]').forEach((b) => b.classList.remove('active'));
                btn.classList.add('active');
                state.applicationFilter = btn.dataset.appFilter;
                renderManagedApplicationsList();
            });
        });
        document.getElementById('closeRejectionModalBtn')?.addEventListener('click', closeRejectionModal);
        document.getElementById('cancelRejectionBtn')?.addEventListener('click', closeRejectionModal);
        document.getElementById('rejectionModal')?.addEventListener('click', (e) => {
            if (e.target.id === 'rejectionModal') closeRejectionModal();
        });
        document.querySelectorAll('.reason-chip').forEach((chip) => {
            chip.addEventListener('click', () => {
                document.querySelectorAll('.reason-chip').forEach((c) => c.classList.remove('selected'));
                chip.classList.add('selected');
                const textarea = document.getElementById('rejectionReasonInput');
                if (textarea) textarea.value = chip.dataset.reason;
            });
        });
        document.getElementById('confirmRejectionBtn')?.addEventListener('click', handleConfirmRejection);
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
        if (post.status === 'CLOSED') {
            const closedBanner = document.createElement('div');
            closedBanner.className = 'adoption-closed-banner';
            closedBanner.style.cssText = 'background: #e6fffa; color: #234e52; border: 1px solid #b2f5ea; border-radius: 8px; padding: 12px 16px; margin: 12px 0; font-weight: 600; display: flex; align-items: center; gap: 8px; font-size: 0.95rem;';
            closedBanner.innerHTML = 'Bé đã tìm được mái ấm và hoàn tất nhận nuôi thành công!';
            content.append(closedBanner);
        }
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
        try {
            state.managedApplications = await request('/api/adoptions/applications/manage');
            updateApplicationFilterCounts();
            renderManagedApplicationsList();
        } catch (error) {
            showError(error.message);
        }
    }

    function updateApplicationFilterCounts() {
        const apps = state.managedApplications || [];
        const total = apps.length;
        const pending = apps.filter(a => a.status === 'PENDING').length;
        const approved = apps.filter(a => a.status === 'APPROVED').length;
        const rejected = apps.filter(a => a.status === 'REJECTED').length;

        const totalEl = document.getElementById('totalAppsCount');
        if (totalEl) totalEl.textContent = String(total);
        const pendingEl = document.getElementById('pendingAppsCount');
        if (pendingEl) pendingEl.textContent = String(pending);
        const approvedEl = document.getElementById('approvedAppsCount');
        if (approvedEl) approvedEl.textContent = String(approved);
        const rejectedEl = document.getElementById('rejectedAppsCount');
        if (rejectedEl) rejectedEl.textContent = String(rejected);
    }

    function renderManagedApplicationsList() {
        const container = document.getElementById('managed-applications');
        if (!container) return;
        const filter = state.applicationFilter || 'ALL';
        const all = state.managedApplications || [];
        const filtered = filter === 'ALL' ? all : all.filter(a => a.status === filter);
        renderApplications(container, filtered, true);
    }

    function openRejectionModal(application) {
        state.rejectionTargetApp = application;
        const modal = document.getElementById('rejectionModal');
        const info = document.getElementById('rejectionModalInfo');
        const textarea = document.getElementById('rejectionReasonInput');
        if (info) {
            info.textContent = `Đơn của khách: ${application.applicantName || 'Khách hàng'} · Bé cún: ${application.dogName || `#${application.adoptionPostId}`}`;
        }
        if (textarea) textarea.value = '';
        document.querySelectorAll('.reason-chip').forEach(c => c.classList.remove('selected'));
        if (modal) modal.hidden = false;
    }

    function closeRejectionModal() {
        const modal = document.getElementById('rejectionModal');
        if (modal) modal.hidden = true;
        state.rejectionTargetApp = null;
    }

    async function handleConfirmRejection() {
        const textarea = document.getElementById('rejectionReasonInput');
        const reason = textarea ? textarea.value.trim() : '';
        if (!reason) {
            window.alert('Vui lòng nhập hoặc chọn lý do từ chối để gửi thông báo cho khách hàng.');
            textarea?.focus();
            return;
        }
        if (!state.rejectionTargetApp) return;
        const appId = state.rejectionTargetApp.id;
        const confirmBtn = document.getElementById('confirmRejectionBtn');
        if (confirmBtn) confirmBtn.disabled = true;
        try {
            await request(`/api/adoptions/applications/${appId}/reject`, {
                method: 'PUT',
                body: { reason }
            });
            closeRejectionModal();
            showNotice('Đơn nhận nuôi đã được từ chối và lý do đã được thông báo về cho khách hàng.');
            await loadManagedApplications();
        } catch (error) {
            showError(error.message);
        } finally {
            if (confirmBtn) confirmBtn.disabled = false;
        }
    }

    async function handleApproveApplication(application) {
        if (!window.confirm(`Xác nhận duyệt đơn nhận nuôi bé "${application.dogName || 'này'}" cho khách hàng "${application.applicantName || 'này'}"?

Sau khi duyệt, thông tin khách hàng sẽ được lưu tại mục "Đã duyệt" để bạn liên hệ nhận bé, và thông báo sẽ được gửi cho khách.`)) {
            return;
        }
        try {
            await request(`/api/adoptions/applications/${application.id}/approve`, { method: 'PUT' });
            showNotice('Đơn nhận nuôi đã được duyệt thành công! Thông báo đã gửi cho khách hàng và thông tin liên hệ đã được lưu lại.');
            await loadManagedApplications();
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
            const card = document.createElement('article');
            card.className = 'application-row';

            // Top Header: Dog info & Status badge
            const headerRow = document.createElement('div');
            headerRow.className = 'app-header-row';

            const dogInfo = document.createElement('div');
            dogInfo.className = 'app-dog-info';
            dogInfo.innerHTML = `<i class="fas fa-paw"></i> <span>${escapeHtml(application.dogName || 'Chó nhận nuôi')}</span>`;
            if (application.branchName) {
                const branchBadge = document.createElement('span');
                branchBadge.className = 'app-branch-badge';
                branchBadge.textContent = application.branchName;
                dogInfo.appendChild(branchBadge);
            }
            headerRow.appendChild(dogInfo);
            headerRow.appendChild(statusElement(application.status));
            card.appendChild(headerRow);

            if (manageable) {
                // STAFF VIEW: Show Customer Contact Info
                const contactCard = document.createElement('div');
                contactCard.className = 'applicant-info-card';
                contactCard.innerHTML = `
                    <div class="applicant-field">
                        <i class="fas fa-user"></i>
                        <span>Khách: <strong>${escapeHtml(application.applicantName || 'Khách hàng')}</strong></span>
                    </div>
                    <div class="applicant-field">
                        <i class="fas fa-phone-alt"></i>
                        <span>SĐT: ${application.applicantPhone ? `<a href="tel:${escapeHtml(application.applicantPhone)}">${escapeHtml(application.applicantPhone)}</a>` : '<em class="text-muted">Chưa cung cấp</em>'}</span>
                    </div>
                    <div class="applicant-field">
                        <i class="fas fa-envelope"></i>
                        <span>Email: ${application.applicantEmail ? `<a href="mailto:${escapeHtml(application.applicantEmail)}">${escapeHtml(application.applicantEmail)}</a>` : '<em class="text-muted">Chưa cung cấp</em>'}</span>
                    </div>
                `;
                card.appendChild(contactCard);

                // Customer's Message
                const msgBox = document.createElement('blockquote');
                msgBox.className = 'app-message-box';
                msgBox.innerHTML = `<strong>Lời nhắn:</strong> ${escapeHtml(application.message || 'Không có lời nhắn.')}`;
                card.appendChild(msgBox);

                // If APPROVED: Show prominent contact & pickup instructions box
                if (application.status === 'APPROVED') {
                    const approvedBox = document.createElement('div');
                    approvedBox.className = 'approved-contact-box';
                    approvedBox.innerHTML = `
                        <div class="approved-contact-title">
                            <i class="fas fa-check-circle"></i>
                            <span>ĐƠN ĐÃ DUYỆT - LIÊN HỆ KHÁCH HÀNG ĐÓN BÉ</span>
                        </div>
                        <div class="approved-contact-details">
                            ${application.applicantPhone ? `<a href="tel:${escapeHtml(application.applicantPhone)}"><i class="fas fa-phone-alt"></i> Gọi ${escapeHtml(application.applicantPhone)}</a>` : ''}
                            ${application.applicantEmail ? `<a href="mailto:${escapeHtml(application.applicantEmail)}"><i class="fas fa-envelope"></i> Gửi email</a>` : ''}
                            <button type="button" class="secondary-button copy-phone-btn" style="padding: 0.35rem 0.8rem; font-size: 0.88rem; border-radius: 8px;">
                                <i class="fas fa-copy"></i> Sao chép SĐT
                            </button>
                        </div>
                        <p class="approved-contact-hint">
                            <i class="fas fa-info-circle"></i> Thông tin khách hàng đã được lưu lại tại đây. Vui lòng liên hệ khách để hẹn thời gian qua chi nhánh hoàn tất thủ tục và đón bé cún.
                        </p>
                    `;
                    approvedBox.querySelector('.copy-phone-btn')?.addEventListener('click', () => {
                        if (application.applicantPhone) {
                            navigator.clipboard.writeText(application.applicantPhone);
                            window.alert(`Đã sao chép số điện thoại: ${application.applicantPhone}`);
                        }
                    });
                    card.appendChild(approvedBox);
                }

                // If REJECTED: Show rejection reason
                if (application.status === 'REJECTED') {
                    const rejectBox = document.createElement('div');
                    rejectBox.className = 'rejection-reason-box';
                    rejectBox.innerHTML = `<strong><i class="fas fa-times-circle"></i> Lý do từ chối:</strong> <span>${escapeHtml(application.rejectionReason || 'Chưa ghi nhận lý do cụ thể')}</span>`;
                    card.appendChild(rejectBox);
                }

                // Date and Action links
                const details = document.createElement('div');
                details.className = 'application-actions';
                const postLink = document.createElement('a');
                postLink.className = 'secondary-button';
                postLink.href = `/adoptions/${application.adoptionPostId}`;
                postLink.textContent = `Xem bài nhận nuôi #${application.adoptionPostId}`;
                details.appendChild(postLink);

                const dateText = document.createElement('small');
                dateText.textContent = `${application.status === 'PENDING' ? 'Gửi lúc' : 'Cập nhật lúc'}: ${formatDate(application.status === 'PENDING' ? application.createdAt : application.updatedAt)}`;
                card.appendChild(dateText);

                if (application.status === 'PENDING') {
                    const approveBtn = document.createElement('button');
                    approveBtn.type = 'button';
                    approveBtn.className = 'btn-approve';
                    approveBtn.innerHTML = '<i class="fas fa-check"></i> Duyệt đơn';
                    approveBtn.addEventListener('click', () => handleApproveApplication(application));

                    const rejectBtn = document.createElement('button');
                    rejectBtn.type = 'button';
                    rejectBtn.className = 'btn-reject';
                    rejectBtn.innerHTML = '<i class="fas fa-times"></i> Từ chối';
                    rejectBtn.addEventListener('click', () => openRejectionModal(application));

                    details.appendChild(approveBtn);
                    details.appendChild(rejectBtn);
                }

                card.appendChild(details);

            } else {
                // CUSTOMER VIEW:
                const msgBox = document.createElement('blockquote');
                msgBox.className = 'app-message-box';
                msgBox.innerHTML = `<strong>Lời nhắn của bạn:</strong> ${escapeHtml(application.message || 'Không có lời nhắn.')}`;
                card.appendChild(msgBox);

                // Status Banner
                const banner = document.createElement('div');
                if (application.status === 'APPROVED') {
                    banner.className = 'customer-adoption-banner approved';
                    banner.innerHTML = `
                        <i class="fas fa-check-circle"></i>
                        <div>
                            <strong>🎉 Đơn nhận nuôi của bạn đã được duyệt!</strong>
                            <p style="margin: 4px 0 0; font-size: 0.9rem;">Vui lòng kiểm tra inbox (tin nhắn) hoặc đợi nhân viên chi nhánh liên hệ qua số điện thoại để trao đổi thêm và hẹn lịch đón bé cún về nhà nhé.</p>
                        </div>
                    `;
                } else if (application.status === 'REJECTED') {
                    banner.className = 'customer-adoption-banner rejected';
                    banner.innerHTML = `
                        <i class="fas fa-times-circle"></i>
                        <div>
                            <strong>❌ Đơn nhận nuôi đã bị từ chối</strong>
                            <p style="margin: 4px 0 0; font-size: 0.9rem;"><strong>Lý do từ chối:</strong> ${escapeHtml(application.rejectionReason || 'Chưa đáp ứng điều kiện nhận nuôi của chi nhánh.')}</p>
                        </div>
                    `;
                } else {
                    banner.className = 'customer-adoption-banner pending';
                    banner.innerHTML = `
                        <i class="fas fa-hourglass-half"></i>
                        <div>
                            <strong>⏳ Đang chờ chi nhánh xét duyệt</strong>
                            <p style="margin: 4px 0 0; font-size: 0.9rem;">Đơn đăng ký của bạn đã được gửi tới nhân viên chi nhánh. Bạn sẽ nhận được thông báo khi đơn được xử lý.</p>
                        </div>
                    `;
                }
                card.appendChild(banner);

                const details = document.createElement('div');
                details.className = 'application-actions';
                const postLink = document.createElement('a');
                postLink.className = 'secondary-button';
                postLink.href = `/adoptions/${application.adoptionPostId}`;
                postLink.textContent = `Xem bài nhận nuôi #${application.adoptionPostId}`;
                details.appendChild(postLink);

                const dateText = document.createElement('small');
                dateText.textContent = `Gửi lúc: ${formatDate(application.createdAt)}`;
                card.appendChild(dateText);

                card.appendChild(details);
            }

            container.appendChild(card);
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
            const postApplications = (applications || []).filter((app) => Number(app.adoptionPostId) === Number(state.currentPost.id));
            const approvedApp = postApplications.find((app) => app.status === 'APPROVED');
            const pendingApp = postApplications.find((app) => app.status === 'PENDING');

            if (approvedApp) {
                form.hidden = true;
                hint.hidden = false;
                hint.className = 'customer-adoption-banner approved';
                hint.innerHTML = '<i class="fas fa-check-circle"></i> <div><strong>🎉 Đơn đăng ký đã được duyệt!</strong><p style="margin: 2px 0 0; font-size: 0.9rem;">Vui lòng kiểm tra inbox (tin nhắn) hoặc đợi nhân viên chi nhánh liên hệ để trao đổi thêm về việc đón bé.</p></div>';
            } else if (pendingApp) {
                form.hidden = true;
                hint.hidden = false;
                hint.className = 'customer-adoption-banner pending';
                hint.innerHTML = '<i class="fas fa-hourglass-half"></i> <div><strong>Đơn đang chờ xét duyệt</strong><p style="margin: 2px 0 0; font-size: 0.9rem;">Bạn đã gửi đơn đăng ký cho bài này. Đơn đang chờ nhân viên chi nhánh xử lý.</p></div>';
            } else {
                // Đơn bị từ chối (hoặc chưa gửi): chỉ lưu vết trong 'Đơn của tôi => Đơn nhận nuôi',
                // còn ở chi tiết bài viết thì vẫn mở form để khách có thể gửi lại đơn mới nếu bài còn AVAILABLE
                hint.hidden = true;
                form.hidden = state.currentPost.status !== 'AVAILABLE';
            }
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
        badge.textContent = ({ AVAILABLE: 'Đang cần được nhận nuôi', CLOSED: 'Bé đã được nhận nuôi', PENDING: 'Đang chờ', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối' })[status] || status;
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
    function escapeHtml(value) { if (!value) return ''; const div = document.createElement('div'); div.textContent = value; return div.innerHTML; }
})();
