(function () {
    const ACCESS_TOKEN_KEY = 'pawconnect.accessToken';
    const form = document.getElementById('profile-form');
    const avatarFile = document.getElementById('avatar-file');
    let user;

    document.addEventListener('DOMContentLoaded', () => {
        document.getElementById('choose-avatar')?.addEventListener('click', () => avatarFile?.click());
        avatarFile?.addEventListener('change', previewAvatar);
        form?.addEventListener('submit', saveProfile);
        loadProfile();
    });

    function headers() { const token = localStorage.getItem(ACCESS_TOKEN_KEY); return token ? { Authorization: `Bearer ${token}` } : {}; }

    async function loadProfile() {
        if (!localStorage.getItem(ACCESS_TOKEN_KEY)) return redirectToLogin();
        const response = await fetch('/api/auth/me', { headers: headers() });
        if (!response.ok) return redirectToLogin();
        user = await response.json();
        document.getElementById('profile-full-name').value = user.fullName || '';
        document.getElementById('profile-phone').value = user.phone || '';
        document.getElementById('profile-email').textContent = user.email || '-';
        document.getElementById('profile-username').textContent = user.username || '-';
        renderAvatar(user.avatarUrl);
    }

    async function saveProfile(event) {
        event.preventDefault();
        const fullName = document.getElementById('profile-full-name').value.trim();
        const phone = normalizePhone(document.getElementById('profile-phone').value);
        if (!fullName) return showMessage('Vui lòng nhập họ và tên.', true);
        if (!validPhone(phone)) return showMessage('Số điện thoại chưa đúng định dạng Việt Nam.', true);
        const saveButton = form.querySelector('button[type="submit"]');
        saveButton.disabled = true;
        try {
            const profileResponse = await fetch('/api/auth/profile', { method: 'PUT', headers: { ...headers(), 'Content-Type': 'application/json' }, body: JSON.stringify({ fullName, phone }) });
            if (!profileResponse.ok) throw new Error(await responseMessage(profileResponse, 'Không thể lưu thông tin.'));
            user = await profileResponse.json();
            if (avatarFile.files?.[0]) await uploadAvatar(avatarFile.files[0]);
            document.getElementById('profile-phone').value = user.phone;
            document.dispatchEvent(new Event('pawconnect:profile-updated'));
            showMessage('Đã cập nhật hồ sơ PawConnect.', false);
        } catch (error) { showMessage(error.message, true); } finally { saveButton.disabled = false; }
    }

    async function uploadAvatar(file) {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch('/api/media/avatar', { method: 'PUT', headers: headers(), body: formData });
        if (!response.ok) throw new Error(await responseMessage(response, 'Không thể tải ảnh đại diện lên.'));
        const asset = await response.json();
        user.avatarUrl = asset.secureUrl;
        renderAvatar(asset.secureUrl);
        avatarFile.value = '';
    }

    function previewAvatar() {
        const file = avatarFile.files?.[0];
        if (!file) return;
        if (!['image/jpeg', 'image/png', 'image/gif'].includes(file.type) || file.size > 5 * 1024 * 1024) {
            avatarFile.value = '';
            showMessage('Ảnh phải là JPG, PNG hoặc GIF và không vượt quá 5 MB.', true);
            return;
        }
        const reader = new FileReader();
        reader.onload = () => renderAvatar(reader.result);
        reader.readAsDataURL(file);
    }

    function renderAvatar(url) {
        const preview = document.getElementById('avatar-preview');
        preview.replaceChildren();
        if (url) { const image = document.createElement('img'); image.src = url; image.alt = 'Ảnh đại diện'; preview.append(image); }
        else preview.innerHTML = '<i class="fas fa-user"></i>';
    }

    function normalizePhone(value) { const compact = value.trim().replace(/[\s().-]/g, ''); return compact.startsWith('+84') ? `0${compact.slice(3)}` : compact; }
    function validPhone(value) { return /^(03|05|07|08|09)\d{8}$/.test(value); }
    function redirectToLogin() { window.location.replace(`/auth/login?returnTo=${encodeURIComponent('/profile')}`); }
    function showMessage(message, error) { const box = document.getElementById('profile-message'); box.textContent = message; box.className = `profile-message ${error ? 'error' : 'success'}`; box.hidden = false; }
    async function responseMessage(response, fallback) { try { return (await response.json()).message || fallback; } catch (_) { return fallback; } }
})();
