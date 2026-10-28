(function () {
    const API = '/api/v1/auth';
    const ACCESS = 'pawconnect.accessToken';
    const REFRESH = 'pawconnect.refreshToken';
    const USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,50}$/;
    const USERNAME_ERROR = 'Tên đăng nhập cần 3–50 ký tự, chỉ dùng chữ không dấu, số, dấu chấm (.), gạch dưới (_) hoặc gạch ngang (-); không có dấu cách.';
    const state = { identity: '', resetToken: null, cooldown: null };
    const path = window.location.pathname;
    const formForPath = path === '/auth/register' ? 'register-form' : path === '/auth/verify-email' ? 'verify-form'
        : path === '/auth/forgot-password' ? 'forgot-form' : 'login-form';

    document.addEventListener('DOMContentLoaded', () => {
        document.getElementById(formForPath).hidden = false;
        bindForms();
        bindUsernameValidation();
        bindOtpDigits();
        bindPasswordChecklist('register-password', 'password-checklist');
        bindPasswordChecklist('reset-password', 'reset-password-checklist');
    });

    function bindForms() {
        byId('login-form').addEventListener('submit', safely(login));
        byId('register-form').addEventListener('submit', safely(register));
        byId('verify-form').addEventListener('submit', safely(verifyEmail));
        byId('resend-verification').addEventListener('click', safely(resendVerification));
        byId('forgot-form').addEventListener('submit', safely(forgotPassword));
        byId('reset-verify-form').addEventListener('submit', safely(verifyResetOtp));
        byId('resend-reset-otp').addEventListener('click', safely(resendResetOtp));
        byId('reset-password-form').addEventListener('submit', safely(resetPassword));
    }

    async function register(event) {
        event.preventDefault();
        if (!validateRegisterUsername(true)) {
            byId('register-username').focus();
            return;
        }
        const values = formValues(event.currentTarget);
        const phone = normalizePhone(values.phone);
        if (!validPhone(phone)) return message('Số điện thoại chưa đúng định dạng Việt Nam.', true);
        if (!passwordValid(values.password, values.confirmPassword)) return message('Mật khẩu chưa đáp ứng đầy đủ yêu cầu.', true);
        const response = await request('/register', { ...values, phone });
        state.identity = values.email;
        byId('verify-identity').value = values.email;
        show('verify-form');
        message(response.message, !response.emailSent);
    }

    async function verifyEmail(event) {
        event.preventDefault();
        const values = formValues(event.currentTarget);
        await request('/verify-email', values);
        message('Xác minh thành công. Đang chuyển đến trang đăng nhập.', false, true);
        setTimeout(() => window.location.assign('/auth/login'), 900);
    }

    async function resendVerification() {
        const identity = byId('verify-identity').value.trim();
        if (!identity) return message('Vui lòng nhập email hoặc tên đăng nhập.', true);
        const response = await request('/resend-verification', { identity });
        message(response.message, !response.emailSent);
        if (response.emailSent) cooldown(60);
    }

    async function login(event) {
        event.preventDefault();
        const response = await request('/login', formValues(event.currentTarget));
        localStorage.setItem(ACCESS, response.accessToken);
        localStorage.setItem(REFRESH, response.refreshToken);
        const returnTo = new URLSearchParams(window.location.search).get('returnTo');
        window.location.assign(returnTo && returnTo.startsWith('/') ? returnTo : '/shop');
    }

    async function forgotPassword(event) {
        event.preventDefault();
        state.identity = formValues(event.currentTarget).identity;
        const response = await request('/forgot-password', { identity: state.identity });
        message(response.message, false, true);
        show('reset-verify-form');
        cooldown(60, 'resend-reset-otp');
    }

    async function resendResetOtp() {
        if (!state.identity) return message('Vui lòng bắt đầu lại từ bước quên mật khẩu.', true);
        const response = await request('/forgot-password', { identity: state.identity });
        message(response.message, false, true);
        cooldown(60, 'resend-reset-otp');
    }

    async function verifyResetOtp(event) {
        event.preventDefault();
        const response = await request('/verify-reset-otp', { identity: state.identity, ...formValues(event.currentTarget) });
        state.resetToken = response.resetToken;
        show('reset-password-form');
        message(response.message, false, true);
    }

    async function resetPassword(event) {
        event.preventDefault();
        const values = formValues(event.currentTarget);
        if (!passwordValid(values.newPassword, values.confirmPassword)) return message('Mật khẩu chưa đáp ứng đầy đủ yêu cầu.', true);
        await request('/reset-password', { resetToken: state.resetToken, ...values });
        state.resetToken = null;
        message('Đặt lại mật khẩu thành công. Đang chuyển đến đăng nhập.', false, true);
        setTimeout(() => window.location.assign('/auth/login'), 900);
    }

    async function request(path, body) {
        clearMessage();
        const response = await fetch(API + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) throwMessage(payload.message || Object.values(payload.errors || {})[0] || 'Yêu cầu không thành công.');
        return payload;
    }

    function bindOtpDigits() { document.querySelectorAll('input[inputmode="numeric"]').forEach((input) => input.addEventListener('input', () => { input.value = input.value.replace(/\D/g, '').slice(0, 6); })); }
    function bindUsernameValidation() {
        const input = byId('register-username');
        if (!input) return;
        input.addEventListener('input', () => {
            const box = byId('auth-message');
            if (!box.hidden && /username format is invalid|tên đăng nhập/i.test(box.textContent)) clearMessage();
            validateRegisterUsername(input.dataset.touched === 'true');
        });
        input.addEventListener('blur', () => {
            input.dataset.touched = 'true';
            validateRegisterUsername(true);
        });
        input.addEventListener('invalid', (event) => {
            event.preventDefault();
            input.dataset.touched = 'true';
            validateRegisterUsername(true);
            input.focus();
        });
    }
    function validateRegisterUsername(showError) {
        const input = byId('register-username');
        const error = byId('register-username-error');
        const valid = USERNAME_PATTERN.test(input.value);
        input.setCustomValidity(valid ? '' : USERNAME_ERROR);
        error.hidden = valid || !showError;
        error.textContent = error.hidden ? '' : USERNAME_ERROR;
        if (error.hidden) input.removeAttribute('aria-invalid');
        else input.setAttribute('aria-invalid', 'true');
        return valid;
    }
    function bindPasswordChecklist(inputId, listId) {
        const input = byId(inputId); const list = byId(listId); if (!input || !list) return;
        const render = () => { const checks = [['Ít nhất 8 ký tự', input.value.length >= 8], ['Có chữ hoa', /[A-Z]/.test(input.value)], ['Có chữ thường', /[a-z]/.test(input.value)], ['Có chữ số', /\d/.test(input.value)], ['Có ký tự @#$%^&+=!', /[@#$%^&+=!]/.test(input.value)]]; list.innerHTML = checks.map(([label, met]) => `<li class="${met ? 'met' : ''}">${label}</li>`).join(''); };
        input.addEventListener('input', render); render();
    }
    function passwordValid(password, confirmation) { return password.length >= 8 && /[A-Z]/.test(password) && /[a-z]/.test(password) && /\d/.test(password) && /[@#$%^&+=!]/.test(password) && password === confirmation; }
    function normalizePhone(value) { const compact = value.trim().replace(/[\s().-]/g, ''); return compact.startsWith('+84') ? `0${compact.slice(3)}` : compact; }
    function validPhone(value) { return /^(03|05|07|08|09)\d{8}$/.test(value); }
    function formValues(form) { return Object.fromEntries(new FormData(form).entries()); }
    function safely(handler) {
        return (event) => Promise.resolve(handler(event)).catch(() => {
            // request() has already rendered a safe message for expected API errors.
        });
    }
    function byId(id) { return document.getElementById(id); }
    function show(id) { document.querySelectorAll('.auth-form').forEach((form) => { form.hidden = form.id !== id; }); }
    function message(text, error, success) { const box = byId('auth-message'); box.textContent = text; box.className = `auth-message ${error ? 'error' : success ? 'success' : ''}`; box.hidden = false; }
    function clearMessage() { byId('auth-message').hidden = true; }
    function throwMessage(text) { message(text, true); throw new Error(text); }
    function cooldown(seconds, buttonId = 'resend-verification') { const button = byId(buttonId); button.disabled = true; clearInterval(state.cooldown); state.cooldown = setInterval(() => { button.textContent = `Gửi lại OTP (${seconds}s)`; seconds -= 1; if (seconds < 0) { clearInterval(state.cooldown); button.disabled = false; button.textContent = 'Gửi lại OTP'; } }, 1000); }
})();
