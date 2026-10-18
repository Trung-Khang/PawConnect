(function () {
    const TOKEN_KEY = 'pawconnect.accessToken';
    const AVATAR_MAP_KEY = 'pawconnect.avatar_map';
    const avatarCache = new Map();
    const state = { user: null, conversations: [], selectedId: null, client: null, poll: null, prefill: '', historyRequest: 0, historyLoadingId: null, openingConversation: false, sending: false, subscriptions: new Set() };

    document.addEventListener('pawconnect:authenticated', (event) => initialize(event.detail));
    document.addEventListener('pawconnect:profile-updated', reloadUser);
    document.addEventListener('pawconnect:signed-out', destroy);
    document.addEventListener('DOMContentLoaded', () => {
        if (localStorage.getItem(TOKEN_KEY)) fetch('/api/auth/me', { headers: headers() })
            .then((response) => response.ok ? response.json() : null).then((user) => user && initialize(user)).catch(() => {});
    });

    function headers() {
        const token = localStorage.getItem(TOKEN_KEY);
        return token ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } : {};
    }

    function saveAvatarMapping(id, name, avatarUrl) {
        if (!avatarUrl) return;
        try {
            const map = JSON.parse(localStorage.getItem(AVATAR_MAP_KEY) || '{}');
            if (id) map[String(id)] = avatarUrl;
            if (name) map[name.trim()] = avatarUrl;
            localStorage.setItem(AVATAR_MAP_KEY, JSON.stringify(map));
        } catch (_) {}
    }

    function getAvatarMapping(id, name) {
        try {
            const map = JSON.parse(localStorage.getItem(AVATAR_MAP_KEY) || '{}');
            if (id && map[String(id)]) return map[String(id)];
            if (name && map[name.trim()]) return map[name.trim()];
        } catch (_) {}
        return null;
    }

    function initialize(user) {
        state.user = user;
        if (user && user.avatarUrl) {
            avatarCache.set(user.id, user.avatarUrl);
            saveAvatarMapping(user.id, user.fullName, user.avatarUrl);
            saveAvatarMapping(user.id, user.username, user.avatarUrl);
        }
        if (!document.getElementById('chatWidgetToggle')) createToggle();
        refresh();
        clearInterval(state.poll); state.poll = setInterval(refresh, 10000);
        connectRealtime();
        primeAdminAvatars();
    }

    async function reloadUser() {
        if (!localStorage.getItem(TOKEN_KEY)) return;
        try {
            const res = await fetch('/api/auth/me', { headers: headers() });
            if (res.ok) {
                const user = await res.json();
                state.user = user;
                if (user && user.avatarUrl) {
                    avatarCache.set(user.id, user.avatarUrl);
                    saveAvatarMapping(user.id, user.fullName, user.avatarUrl);
                    saveAvatarMapping(user.id, user.username, user.avatarUrl);
                }
                if (document.getElementById('chatWidgetDrawer')) syncOpenDrawer();
            }
        } catch (_) {}
    }

    async function primeAdminAvatars() {
        if (state.user && state.user.role === 'ADMIN') {
            try {
                const res = await fetch('/api/admin/users', { headers: headers() });
                if (res.ok) {
                    const list = await res.json();
                    list.forEach((u) => {
                        if (u.id && u.avatarUrl) {
                            avatarCache.set(u.id, u.avatarUrl);
                            saveAvatarMapping(u.id, u.fullName, u.avatarUrl);
                            saveAvatarMapping(u.id, u.username, u.avatarUrl);
                        }
                    });
                }
            } catch (_) {}
        }
    }

    function destroy() {
        clearInterval(state.poll); state.client?.deactivate();
        state.user = null; state.client = null; state.conversations = []; state.selectedId = null; state.historyLoadingId = null; state.openingConversation = false; state.subscriptions.clear();
        document.getElementById('chatWidgetToggle')?.remove(); close();
    }

    function createToggle() {
        const button = el('button', 'chat-widget-toggle');
        button.id = 'chatWidgetToggle'; button.type = 'button'; button.title = 'Chat với nhân viên'; button.setAttribute('aria-label', 'Chat với nhân viên'); button.append(icon('fas fa-phone'));
        const badge = el('span', 'chat-widget-badge'); badge.id = 'chatWidgetBadge'; badge.hidden = true; button.append(badge);
        button.addEventListener('click', open); document.body.append(button);
    }

    async function refresh() {
        if (!state.user) return;
        try {
            const response = await fetch('/api/conversations/my', { headers: headers() });
            if (response.status === 401) return destroy();
            if (!response.ok) return;
            state.conversations = await response.json();

            // Deduplicate conversations by ID
            const seen = new Set();
            state.conversations = state.conversations.filter((c) => {
                if (seen.has(c.id)) return false;
                seen.add(c.id);
                return true;
            });

            if (state.selectedId && !selected()) state.selectedId = null;
            if (state.user.role === 'CUSTOMER' && !state.selectedId && state.conversations.length > 0) {
                state.selectedId = state.conversations[0].id;
            }
            state.conversations.forEach((item) => subscribe(item.id));
            updateBadge();
            updateActiveConversationStatus();
            if (document.getElementById('chatWidgetDrawer')) syncOpenDrawer();
        } catch (_) {
            // A failed background refresh must never replace the active message composer.
        }
    }

    function updateBadge() {
        const count = state.conversations.reduce((total, item) => total + Number(item.unreadCount || 0), 0);
        const badge = document.getElementById('chatWidgetBadge'); const toggle = document.getElementById('chatWidgetToggle');
        if (!badge || !toggle) return;
        badge.hidden = count === 0; badge.textContent = count > 9 ? '9+' : String(count); toggle.classList.toggle('has-unread', count > 0);
    }

    function open() {
        if (document.getElementById('chatWidgetDrawer')) return;
        const backdrop = el('div', 'chat-widget-backdrop'); backdrop.id = 'chatWidgetBackdrop'; backdrop.addEventListener('click', close);
        const drawer = el('aside', 'chat-widget-drawer'); drawer.id = 'chatWidgetDrawer'; drawer.setAttribute('aria-label', (state.user && state.user.role !== 'CUSTOMER') ? 'Hộp thư tin nhắn' : 'Chat với nhân viên');

        const header = el('div', 'chat-widget-header');
        const body = el('div', 'chat-widget-body'); body.append(el('section', 'chat-widget-panel'));
        drawer.append(header, body); document.body.append(backdrop, drawer);
        updateHeader();
        syncOpenDrawer();
    }

    function updateHeader() {
        const header = document.querySelector('#chatWidgetDrawer .chat-widget-header');
        if (!header) return;
        header.replaceChildren();

        const brand = el('div', 'chat-header-brand');

        if (state.user && state.user.role !== 'CUSTOMER' && state.selectedId) {
            const backBtn = el('button', 'chat-back-btn');
            backBtn.type = 'button';
            backBtn.setAttribute('aria-label', 'Quay lại danh sách tin nhắn');
            backBtn.append(icon('fas fa-chevron-left'), el('span', '', 'Quay lại'));
            backBtn.addEventListener('click', () => {
                state.selectedId = null;
                syncOpenDrawer();
            });
            header.append(backBtn);

            const iconSpan = el('span', 'chat-header-icon');
            iconSpan.append(icon('fas fa-paw'));
            brand.append(iconSpan, el('h2', '', 'Chat với khách hàng'));
        } else {
            const iconSpan = el('span', 'chat-header-icon');
            iconSpan.append(icon('fas fa-paw'));
            const title = (state.user && state.user.role !== 'CUSTOMER') ? 'Hộp thư tin nhắn' : 'Chat với nhân viên';
            brand.append(iconSpan, el('h2', '', title));
        }

        const closeButton = el('button', 'chat-widget-close');
        closeButton.type = 'button';
        closeButton.setAttribute('aria-label', 'Đóng cửa sổ chat');
        closeButton.append(icon('fas fa-times'));
        closeButton.addEventListener('click', close);

        header.append(brand, closeButton);
    }

    function close() {
        state.historyRequest++;
        document.getElementById('chatWidgetDrawer')?.remove(); document.getElementById('chatWidgetBackdrop')?.remove();
    }

    function syncOpenDrawer() {
        const panel = document.querySelector('#chatWidgetDrawer .chat-widget-panel');
        if (!panel) return;
        updateHeader();

        if (state.openingConversation) {
            renderOpeningConversation(panel);
            return;
        }

        // If staff and no conversation selected: render inbox list
        if (state.user && state.user.role !== 'CUSTOMER' && !state.selectedId) {
            renderInbox(panel);
            return;
        }

        if (state.selectedId) {
            const messages = panel.querySelector('.chat-widget-messages');
            if (!messages || Number(messages.dataset.conversationId) !== state.selectedId) {
                if (state.historyLoadingId === state.selectedId) return;
                selectConversation(state.selectedId);
            }
            return;
        }

        if (state.conversations.length > 0) {
            state.selectedId = state.conversations[0].id;
            selectConversation(state.selectedId);
            return;
        }

        if (!panel.querySelector('.chat-widget-compose')) {
            renderNewConversation(panel);
        }
    }

    function renderOpeningConversation(panel) {
        if (panel.querySelector('.chat-widget-opening')) return;
        panel.replaceChildren(el('p', 'chat-widget-notice chat-widget-opening', 'Đang mở cuộc trò chuyện...'));
    }

    function formatTime(dateStr) {
        if (!dateStr) return '';
        const d = new Date(dateStr);
        const now = new Date();
        const isToday = d.toDateString() === now.toDateString();
        return isToday
            ? d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
            : d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });
    }

    async function renderInbox(panel) {
        panel.replaceChildren();

        const container = el('div', 'chat-inbox-container');

        // Header bar
        const headerBar = el('div', 'chat-inbox-header-bar');
        const titleSpan = el('span', 'chat-inbox-title');
        titleSpan.append(icon('fas fa-inbox'), el('span', '', 'Danh sách tin nhắn khách'));

        const unreadCount = state.conversations.reduce((acc, c) => acc + (Number(c.unreadCount) || 0), 0);
        const countChip = el('span', 'chat-inbox-chip', unreadCount > 0 ? `${unreadCount} chưa xem` : `${state.conversations.length} cuộc trò chuyện`);
        headerBar.append(titleSpan, countChip);
        container.append(headerBar);

        // Deduplication and Sorting
        const uniqueConversations = [];
        const seenIds = new Set();
        for (const c of state.conversations) {
            if (!seenIds.has(c.id)) {
                seenIds.add(c.id);
                uniqueConversations.push(c);
            }
        }

        // Sort: Unread first (newest message first), then read by newest message first
        uniqueConversations.sort((a, b) => {
            const aUnread = (Number(a.unreadCount) || 0) > 0 ? 1 : 0;
            const bUnread = (Number(b.unreadCount) || 0) > 0 ? 1 : 0;
            if (aUnread !== bUnread) return bUnread - aUnread;
            const aTime = new Date(a.lastMessageAt || a.createdAt || 0).getTime();
            const bTime = new Date(b.lastMessageAt || b.createdAt || 0).getTime();
            return bTime - aTime;
        });

        if (uniqueConversations.length === 0) {
            container.append(el('div', 'chat-inbox-empty', 'Hiện chưa có cuộc trò chuyện nào từ khách hàng.'));
            panel.append(container);
            return;
        }

        const list = el('div', 'chat-inbox-list');
        uniqueConversations.forEach((conv) => {
            const item = el('div', 'chat-inbox-item');
            if (conv.unreadCount > 0) item.classList.add('is-unread');

            const avatarBox = el('div', 'chat-inbox-item-avatar');
            const customerName = conv.customerName || 'Khách hàng';
            const initialChar = customerName.trim().charAt(0).toUpperCase();
            const avatarUrl = conv.customerAvatarUrl || avatarCache.get(conv.customerId) || getAvatarMapping(conv.customerId, customerName);
            if (avatarUrl) {
                avatarCache.set(conv.customerId, avatarUrl);
                saveAvatarMapping(conv.customerId, customerName, avatarUrl);
                const img = document.createElement('img');
                img.src = avatarUrl; img.alt = customerName;
                avatarBox.append(img);
            } else {
                avatarBox.append(el('div', 'chat-avatar-user', initialChar));
            }
            item.append(avatarBox);

            // Content
            const contentBox = el('div', 'chat-inbox-item-content');
            const rowTop = el('div', 'chat-inbox-item-row');
            const nameEl = el('span', 'chat-inbox-item-name', customerName);
            nameEl.title = customerName;
            const timeEl = el('span', 'chat-inbox-item-time', conv.lastMessageAt ? formatTime(conv.lastMessageAt) : '');
            rowTop.append(nameEl, timeEl);

            const msgPreview = el('span', 'chat-inbox-item-msg', conv.lastMessagePreview || 'Chưa có nội dung tin nhắn');
            contentBox.append(rowTop, msgPreview);
            item.append(contentBox);

            if (conv.unreadCount > 0) {
                item.append(el('span', 'chat-inbox-badge', conv.unreadCount > 9 ? '9+' : String(conv.unreadCount)));
            }

            // Click opens conversation
            item.addEventListener('click', () => {
                state.selectedId = conv.id;
                selectConversation(conv.id);
            });

            list.append(item);
        });

        container.append(list);
        panel.append(container);
    }

    function renderNewConversation(panel) {
        panel.replaceChildren();
        const messages = el('div', 'chat-widget-messages');
        messages.append(el('p', 'chat-widget-notice', 'PawConnect luôn sẵn sàng hỗ trợ bạn. Hãy để lại tin nhắn.'));
        panel.append(messages, composer(null, messages));
    }

    async function selectConversation(id, showLoading = true) {
        const panel = document.querySelector('#chatWidgetDrawer .chat-widget-panel');
        if (!panel) return;
        if (state.historyLoadingId === id) return;
        state.selectedId = id;
        updateHeader();
        const request = ++state.historyRequest;
        state.historyLoadingId = id;
        if (showLoading) panel.replaceChildren(el('p', 'chat-widget-notice', 'Đang tải lịch sử...'));
        try {
            const response = await fetch(`/api/conversations/${id}/messages?size=100`, { headers: headers() });
            if (!response.ok) return showError(panel, response);
            const page = await response.json();
            if (request !== state.historyRequest || state.selectedId !== id) return;
            renderConversation(panel, selected(), [...(page.content || [])].reverse());
            subscribe(id);
            refresh();
        } catch (_) {
            if (request === state.historyRequest) {
                panel.replaceChildren(el('p', 'chat-widget-notice', 'Không thể tải nội dung cuộc trò chuyện.'));
            }
        } finally {
            if (state.historyLoadingId === id) state.historyLoadingId = null;
        }
    }

    function renderConversation(panel, conversation, history) {
        panel.replaceChildren();
        const notice = el('p', 'chat-widget-notice', staffStatus(conversation));
        notice.dataset.conversationStatus = 'true';
        panel.append(notice);
        const messages = el('div', 'chat-widget-messages'); messages.dataset.conversationId = String(conversation.id);
        history.forEach((message) => appendMessage(messages, message)); messages.scrollTop = messages.scrollHeight; panel.append(messages);
        if (conversation.status !== 'CLOSED') panel.append(composer(conversation.id, messages));
    }

    function composer(conversationId, messages) {
        const form = el('form', 'chat-widget-compose'); const input = document.createElement('textarea'); input.maxLength = 4000; input.placeholder = 'Nhập tin nhắn';
        if (state.prefill) { input.value = state.prefill; state.prefill = ''; }
        const emoji = el('button', 'chat-emoji-button'); emoji.type = 'button'; emoji.title = 'Chọn biểu tượng cảm xúc'; emoji.setAttribute('aria-label', 'Chọn biểu tượng cảm xúc'); emoji.append(icon('far fa-smile'));
        emoji.addEventListener('click', () => toggleEmojiPicker(form, input));
        const send = el('button'); send.type = 'submit'; send.title = 'Gửi tin nhắn'; send.setAttribute('aria-label', 'Gửi tin nhắn'); send.append(icon('fas fa-paper-plane')); form.append(input, emoji, send);
        input.addEventListener('keydown', (event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); form.requestSubmit(); } });
        form.addEventListener('submit', async (event) => {
            event.preventDefault(); const content = input.value.trim(); if (!content || state.sending) return;
            state.sending = true; send.disabled = true; input.disabled = true;
            try {
                const activeConversationId = conversationId || await createGeneralConversation();
                messages.dataset.conversationId = String(activeConversationId);
                const response = await fetch(`/api/conversations/${activeConversationId}/messages`, { method: 'POST', headers: headers(), body: JSON.stringify({ conversationId: activeConversationId, content }) });
                if (!response.ok) { await showError(form, response); return; }
                const sentMessage = await response.json();
                appendMessage(messages, sentMessage); // WebSocket delivery is deduplicated by message id.
                updateLocalConversationAfterReply(activeConversationId, sentMessage);
                input.value = '';
                refresh();
            } catch (_) {
                form.prepend(el('p', 'chat-widget-notice', 'Không thể gửi tin nhắn. Nội dung vẫn được giữ để bạn thử lại.'));
            } finally {
                state.sending = false; send.disabled = false; input.disabled = false; input.focus();
            }
        });
        return form;
    }

    const EMOJI_CATEGORIES = [
        {
            name: 'Mặt cười & Cảm xúc',
            emojis: ['😀', '😃', '😄', '😁', '😆', '😅', '😂', '🤣', '😊', '😇', '🙂', '😉', '😌', '😍', '🥰', '😘', '😋', '😛', '😜', '🤪', '🤩', '🥳', '😎', '🤗', '🤔', '🤫', '🥺', '😳', '😭', '😱']
        },
        {
            name: 'Cún cưng & Động vật',
            emojis: ['🐶', '🐕', '🐩', '🐾', '🦴', '🐺', '🦊', '🐱', '🐈', '🦁', '🐯', '🐼', '🐨', '🐰', '🐹', '🐭', '🐻', '🐥', '🐣', '🦆', '🦉', '🦮', '🐕‍🦺']
        },
        {
            name: 'Cử chỉ & Trái tim',
            emojis: ['👍', '👎', '👏', '🙌', '🤝', '👋', '✌️', '🤞', '👌', '❤️', '🧡', '💛', '💚', '💙', '💜', '🤎', '🖤', '🤍', '💖', '💗', '💓', '💞', '💕', '💘', '✨', '⭐', '🎉', '🎁', '🙏', '💯']
        }
    ];

    function toggleEmojiPicker(form, input) {
        const existing = form.querySelector('.chat-emoji-picker');
        if (existing) return existing.remove();

        const picker = el('div', 'chat-emoji-picker');
        const header = el('div', 'chat-emoji-header');
        header.append(el('span', '', 'Biểu tượng cảm xúc 🐾'));
        const closeBtn = el('button', 'chat-emoji-close', '✕');
        closeBtn.type = 'button';
        closeBtn.setAttribute('aria-label', 'Đóng chọn cảm xúc');
        closeBtn.addEventListener('click', () => {
            picker.remove();
            document.removeEventListener('click', onOutsideClick);
        });
        header.append(closeBtn);
        picker.append(header);

        const scrollContainer = el('div', 'chat-emoji-scroll');
        EMOJI_CATEGORIES.forEach((category) => {
            scrollContainer.append(el('div', 'chat-emoji-cat-title', category.name));
            const grid = el('div', 'chat-emoji-grid');
            category.emojis.forEach((value) => {
                const choice = el('button', 'chat-emoji-item', value);
                choice.type = 'button';
                choice.title = value;
                choice.addEventListener('click', () => {
                    input.setRangeText(value, input.selectionStart, input.selectionEnd, 'end');
                    picker.remove();
                    document.removeEventListener('click', onOutsideClick);
                    input.focus();
                });
                grid.append(choice);
            });
            scrollContainer.append(grid);
        });
        picker.append(scrollContainer);

        function onOutsideClick(event) {
            if (!picker.contains(event.target) && !event.target.closest('.chat-emoji-button')) {
                picker.remove();
                document.removeEventListener('click', onOutsideClick);
            }
        }
        setTimeout(() => document.addEventListener('click', onOutsideClick), 10);

        form.append(picker);
    }

    async function createGeneralConversation() {
        const branchesResponse = await fetch('/api/branches');
        if (!branchesResponse.ok) throw new Error('Cannot load branch');
        const branches = await branchesResponse.json();
        if (!branches.length) throw new Error('No branch');
        const response = await fetch('/api/conversations/support', { method: 'POST', headers: headers(), body: JSON.stringify({ branchId: branches[0].id }) });
        if (!response.ok) throw new Error('Cannot create conversation');
        const conversation = await response.json(); upsertConversation(conversation); state.selectedId = conversation.id;
        return conversation.id;
    }

    function upsertConversation(conversation) {
        const index = state.conversations.findIndex((item) => item.id === conversation.id);
        if (index >= 0) state.conversations[index] = { ...state.conversations[index], ...conversation };
        else state.conversations.unshift(conversation);
        subscribe(conversation.id);
        updateBadge();
    }

    function appendMessage(container, message) {
        if (!container || container.querySelector(`[data-message-id="${message.id}"]`)) return;
        const item = renderMessage(message); item.dataset.messageId = String(message.id); container.append(item); container.scrollTop = container.scrollHeight;
    }

    function createMessageAvatar(message) {
        const container = el('div', 'chat-avatar-container');
        const isStaff = Boolean(message.senderRole && message.senderRole !== 'CUSTOMER');
        const senderName = message.senderName;

        if (isStaff) {
            const img = document.createElement('img');
            img.className = 'chat-avatar-img chat-avatar-staff';
            img.src = '/images/chat-staff-avatar.png';
            img.alt = senderName || 'Nhân viên';
            img.title = senderName || 'Nhân viên PawConnect';
            img.onerror = () => {
                img.remove();
                const fallback = el('div', 'chat-avatar-staff-fallback');
                fallback.append(icon('fas fa-dog'));
                container.append(fallback);
            };
            container.append(img);
        } else {
            let avatarUrl = avatarCache.get(message.senderId);
            if (!avatarUrl && state.user && state.user.id === message.senderId && state.user.avatarUrl) {
                avatarUrl = state.user.avatarUrl;
                avatarCache.set(message.senderId, avatarUrl);
            }
            if (!avatarUrl && state.user && state.user.id === message.senderId) {
                const headerAvatar = document.getElementById('account-avatar');
                if (headerAvatar && headerAvatar.src && !headerAvatar.hidden) {
                    avatarUrl = headerAvatar.src;
                    avatarCache.set(message.senderId, avatarUrl);
                }
            }

            const initial = (senderName || 'K').trim().charAt(0).toUpperCase();

            if (avatarUrl) {
                const img = document.createElement('img');
                img.className = 'chat-avatar-img chat-avatar-user-img';
                img.src = avatarUrl;
                img.alt = senderName || 'Khách hàng';
                img.title = senderName || 'Khách hàng';
                img.onerror = () => {
                    img.remove();
                    container.append(createInitialAvatar(initial, senderName));
                };
                container.append(img);
            } else {
                container.append(createInitialAvatar(initial, senderName));
            }
        }
        return container;
    }

    function createInitialAvatar(initial, senderName) {
        const avatar = el('div', 'chat-avatar-user', initial);
        avatar.title = senderName || 'Khách hàng';
        return avatar;
    }

    function renderMessage(message) {
        const isMine = Boolean(state.user && message.senderId === state.user.id);
        const isStaff = Boolean(message.senderRole && message.senderRole !== 'CUSTOMER');
        const item = el('article', `chat-widget-message ${isMine ? 'mine' : 'theirs'}`);
        item.dataset.messageId = String(message.id);

        const row = el('div', 'chat-message-row');
        row.append(createMessageAvatar(message));

        const bubbleCol = el('div', 'chat-bubble-col');

        if (!isMine) {
            const senderLabel = el('div', 'chat-sender-label');
            senderLabel.append(el('span', isStaff ? 'chat-staff-name' : '', message.senderName || (isStaff ? 'Tư vấn viên PawConnect' : 'Khách hàng')));
            bubbleCol.append(senderLabel);
        }

        if (message.messageType === 'CONTEXT') {
            const card = el('a', 'chat-context-card');
            card.href = message.contextUrl || '#';
            if (message.contextImageUrl) {
                const img = document.createElement('img');
                img.src = message.contextImageUrl;
                img.alt = message.contextTitle || '';
                card.append(img);
            }
            const info = el('div', 'chat-context-info');
            info.append(
                el('strong', '', message.contextTitle || 'Thông tin tư vấn'),
                el('span', '', message.contextSummary || '')
            );
            card.append(info);
            bubbleCol.append(card);
        }

        const bubble = el('div', 'chat-bubble chat-message-content', message.content);
        bubbleCol.append(bubble);

        const timeStr = message.sentAt ? new Date(message.sentAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : '';
        bubbleCol.append(el('small', 'chat-message-time', timeStr));

        row.append(bubbleCol);
        item.append(row);
        return item;
    }

    function connectRealtime() {
        if (state.client || !window.SockJS || !window.StompJs || !localStorage.getItem(TOKEN_KEY)) return;
        const client = new window.StompJs.Client({ webSocketFactory: () => new window.SockJS('/ws'), connectHeaders: { Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}` }, reconnectDelay: 5000 });
        client.onConnect = () => {
            state.subscriptions.clear();
            client.subscribe('/topic/chat-inbox', () => refresh());
            state.conversations.forEach((item) => subscribe(item.id));
        };
        client.onStompError = () => {}; state.client = client; client.activate();
    }

    function subscribe(id) {
        if (!state.client?.connected || state.subscriptions.has(id)) return;
        state.client.subscribe(`/topic/conversation/${id}`, (frame) => {
            const message = JSON.parse(frame.body); appendMessage(document.querySelector(`.chat-widget-messages[data-conversation-id="${id}"]`), message); refresh();
        });
        state.subscriptions.add(id);
    }

    function selected() { return state.conversations.find((item) => item.id === state.selectedId) || null; }
    function updateLocalConversationAfterReply(conversationId, message) {
        if (!state.user || state.user.role === 'CUSTOMER') return;
        const conversation = state.conversations.find((item) => item.id === conversationId);
        if (!conversation) return;
        conversation.assignedStaffId = state.user.id;
        conversation.assignedStaffName = supportDisplayName(state.user.fullName);
        conversation.lastMessageAt = message.sentAt || new Date().toISOString();
        conversation.lastMessagePreview = message.content || conversation.lastMessagePreview;
        conversation.unreadCount = 0;
        updateBadge();
        updateActiveConversationStatus();
    }
    function updateActiveConversationStatus() {
        const notice = document.querySelector('#chatWidgetDrawer [data-conversation-status="true"]');
        const conversation = selected();
        if (notice && conversation) notice.textContent = staffStatus(conversation);
    }
    function supportDisplayName(name) {
        return ({
            'Quản lý TP.HCM': 'Nhân viên TP.HCM',
            'Quản lý Hà Nội': 'Nhân viên Hà Nội',
            'Quản lý Đà Nẵng': 'Nhân viên Đà Nẵng'
        })[name] || name;
    }
    function staffStatus(item) { return item.status === 'CLOSED' ? 'Cuộc trò chuyện đã đóng.' : (item.assignedStaffName ? `Đang được ${item.assignedStaffName} hỗ trợ.` : 'Đang chờ nhân viên PawConnect hỗ trợ.'); }
    async function showError(parent, response) { let text = 'Thao tác chưa thực hiện được.'; try { text = (await response.json()).message || text; } catch (_) {} parent.prepend(el('p', 'chat-widget-notice', text)); }
    function el(tag, className, text) { const node = document.createElement(tag); if (className) node.className = className; if (text != null) node.textContent = text; return node; }
    function icon(classes) { const node = document.createElement('i'); node.className = classes; node.setAttribute('aria-hidden', 'true'); return node; }

    async function openSupport(context) {
        if (!localStorage.getItem(TOKEN_KEY)) return window.location.assign(`/auth/login?returnTo=${encodeURIComponent(window.location.pathname + window.location.search)}`);
        if (!state.user) { const response = await fetch('/api/auth/me', { headers: headers() }); if (!response.ok) return; initialize(await response.json()); }
        if (state.user.role !== 'CUSTOMER') return window.alert('Tư vấn mua hàng chỉ dành cho tài khoản khách hàng.');
        if (!context.branchId) return;
        return openContextConversation('/api/conversations/support', { branchId: context.branchId, productId: context.productId, puppyListingId: context.puppyListingId }, `Mình muốn được tư vấn về ${context.title}.`);
    }

    async function openAdoption(postId) {
        if (!localStorage.getItem(TOKEN_KEY)) return window.location.assign(`/auth/login?returnTo=${encodeURIComponent(window.location.pathname)}`);
        return openContextConversation(`/api/conversations/adoption/${postId}`, null, 'Mình muốn được tư vấn về việc nhận nuôi này.');
    }

    async function openContextConversation(url, payload, prefill) {
        state.prefill = prefill;
        state.openingConversation = true;
        open();
        syncOpenDrawer();
        const panel = document.querySelector('#chatWidgetDrawer .chat-widget-panel');
        try {
            const response = await fetch(url, {
                method: 'POST', headers: headers(), body: payload ? JSON.stringify(payload) : undefined
            });
            if (!response.ok) {
                state.openingConversation = false;
                syncOpenDrawer();
                return showError(panel, response);
            }
            const conversation = await response.json();
            upsertConversation(conversation);
            state.selectedId = conversation.id;
            state.openingConversation = false;
            await selectConversation(conversation.id, false);
        } catch (_) {
            state.openingConversation = false;
            syncOpenDrawer();
            panel?.prepend(el('p', 'chat-widget-notice', 'Không thể mở cuộc trò chuyện. Vui lòng thử lại.'));
        }
    }

    window.PawConnectChat = { open, openSupport, openAdoption, refresh };
})();
