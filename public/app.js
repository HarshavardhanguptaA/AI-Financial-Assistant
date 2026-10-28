/**
 * Financial Copilot — Vanilla JavaScript Frontend Controller
 * Handles API communication, UI state management, and DOM updates.
 */

(() => {
    'use strict';

    // Currency Formatter
    const currencyFormatter = new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });

    const formatCurrency = (amount) => {
        if (amount === null || amount === undefined || isNaN(amount)) {
            return '₹0.00';
        }
        return currencyFormatter.format(Number(amount));
    };

    // Category options mapping
    const CATEGORIES = {
        EXPENSE: ['Food', 'Travel', 'Education', 'Shopping', 'Bills', 'Entertainment', 'Other'],
        INCOME: ['Salary', 'Stipend', 'Scholarship', 'Freelance', 'Other']
    };

    // DOM Elements Cache
    const elements = {
        currentDateDisplay: document.getElementById('currentDateDisplay'),
        refreshBtn: document.getElementById('refreshBtn'),
        globalNotification: document.getElementById('globalNotification'),
        notificationMessage: document.getElementById('notificationMessage'),
        notificationCloseBtn: document.getElementById('notificationCloseBtn'),

        // Overview Cards
        summaryTotalIncome: document.getElementById('summaryTotalIncome'),
        summaryTotalExpenses: document.getElementById('summaryTotalExpenses'),
        summaryAvailableBalance: document.getElementById('summaryAvailableBalance'),
        summaryRemainingBudget: document.getElementById('summaryRemainingBudget'),
        summaryBalanceSubtext: document.getElementById('summaryBalanceSubtext'),

        // Budget Card
        currentMonthBadge: document.getElementById('currentMonthBadge'),
        metricMonthlyBudget: document.getElementById('metricMonthlyBudget'),
        metricSpentThisMonth: document.getElementById('metricSpentThisMonth'),
        metricRemainingBudget: document.getElementById('metricRemainingBudget'),
        budgetUsagePercentText: document.getElementById('budgetUsagePercentText'),
        budgetProgressFill: document.getElementById('budgetProgressFill'),
        budgetAlertBanner: document.getElementById('budgetAlertBanner'),
        updateBudgetForm: document.getElementById('updateBudgetForm'),
        budgetInput: document.getElementById('budgetInput'),
        saveBudgetBtn: document.getElementById('saveBudgetBtn'),

        // Category Breakdown
        categoryBreakdownContainer: document.getElementById('categoryBreakdownContainer'),

        // Transaction Form
        transactionForm: document.getElementById('transactionForm'),
        typeExpenseBtn: document.getElementById('typeExpenseBtn'),
        typeIncomeBtn: document.getElementById('typeIncomeBtn'),
        txnTypeInput: document.getElementById('txnTypeInput'),
        txnDescription: document.getElementById('txnDescription'),
        txnAmount: document.getElementById('txnAmount'),
        txnCategory: document.getElementById('txnCategory'),
        categoryLabel: document.getElementById('categoryLabel'),
        txnDate: document.getElementById('txnDate'),
        submitTxnBtn: document.getElementById('submitTxnBtn'),

        // Affordability
        affordabilityForm: document.getElementById('affordabilityForm'),
        plannedPurchaseInput: document.getElementById('plannedPurchaseInput'),
        checkAffordabilityBtn: document.getElementById('checkAffordabilityBtn'),
        affordabilityResultContainer: document.getElementById('affordabilityResultContainer'),
        affordabilityStatusBadge: document.getElementById('affordabilityStatusBadge'),
        affordCurrentBalance: document.getElementById('affordCurrentBalance'),
        affordPurchaseCost: document.getElementById('affordPurchaseCost'),
        affordBalanceAfter: document.getElementById('affordBalanceAfter'),
        affordBudgetAfter: document.getElementById('affordBudgetAfter'),
        affordabilityExplanationText: document.getElementById('affordabilityExplanationText'),

        // Insights
        insightsContainer: document.getElementById('insightsContainer'),

        // Transactions Table
        transactionsTableBody: document.getElementById('transactionsTableBody'),
        transactionCountBadge: document.getElementById('transactionCountBadge')
    };

    let notificationTimeout = null;

    // =========================================================================
    // Initialization
    // =========================================================================

    function init() {
        renderCurrentDate();
        initDateInputs();
        setupCategoryDropdown('EXPENSE');
        bindEventListeners();
        fetchAllData();
    }

    function renderCurrentDate() {
        const now = new Date();
        const options = { year: 'numeric', month: 'short', day: 'numeric' };
        if (elements.currentDateDisplay) {
            elements.currentDateDisplay.textContent = now.toLocaleDateString('en-IN', options);
        }
    }

    function initDateInputs() {
        const todayIso = new Date().toISOString().split('T')[0];
        if (elements.txnDate) {
            elements.txnDate.value = todayIso;
            elements.txnDate.max = todayIso;
        }
    }

    function setupCategoryDropdown(type) {
        if (!elements.txnCategory) return;
        const options = CATEGORIES[type] || CATEGORIES.EXPENSE;
        elements.txnCategory.innerHTML = '';
        options.forEach(opt => {
            const el = document.createElement('option');
            el.value = opt;
            el.textContent = opt;
            elements.txnCategory.appendChild(el);
        });

        if (elements.categoryLabel) {
            elements.categoryLabel.textContent = type === 'INCOME' ? 'Income Source' : 'Expense Category';
        }
    }

    // =========================================================================
    // Event Listeners
    // =========================================================================

    function bindEventListeners() {
        // Refresh Button
        if (elements.refreshBtn) {
            elements.refreshBtn.addEventListener('click', () => {
                showNotification('Refreshing dashboard data...', 'info');
                fetchAllData();
            });
        }

        // Notification Close
        if (elements.notificationCloseBtn) {
            elements.notificationCloseBtn.addEventListener('click', hideNotification);
        }

        // Segmented Transaction Type Control
        if (elements.typeExpenseBtn && elements.typeIncomeBtn) {
            elements.typeExpenseBtn.addEventListener('click', () => setTransactionType('EXPENSE'));
            elements.typeIncomeBtn.addEventListener('click', () => setTransactionType('INCOME'));
        }

        // Add Transaction Form Submit
        if (elements.transactionForm) {
            elements.transactionForm.addEventListener('submit', handleAddTransaction);
        }

        // Update Budget Form Submit
        if (elements.updateBudgetForm) {
            elements.updateBudgetForm.addEventListener('submit', handleUpdateBudget);
        }

        // Check Affordability Form Submit
        if (elements.affordabilityForm) {
            elements.affordabilityForm.addEventListener('submit', handleCheckAffordability);
        }
    }

    function setTransactionType(type) {
        elements.txnTypeInput.value = type;
        if (type === 'EXPENSE') {
            elements.typeExpenseBtn.classList.add('active');
            elements.typeIncomeBtn.classList.remove('active');
        } else {
            elements.typeIncomeBtn.classList.add('active');
            elements.typeExpenseBtn.classList.remove('active');
        }
        setupCategoryDropdown(type);
    }

    // =========================================================================
    // API Data Fetching
    // =========================================================================

    async function fetchAllData() {
        try {
            await Promise.all([
                fetchSummary(),
                fetchTransactions(),
                fetchInsights()
            ]);
        } catch (err) {
            console.error('Error fetching dashboard data:', err);
            showNotification('Failed to connect to local server. Ensure Java backend is running.', 'danger');
        }
    }

    async function fetchSummary() {
        const res = await fetch('/api/summary');
        if (!res.ok) throw new Error(`Summary API error: ${res.status}`);
        const data = await res.json();
        renderSummary(data);
    }

    async function fetchTransactions() {
        const res = await fetch('/api/transactions');
        if (!res.ok) throw new Error(`Transactions API error: ${res.status}`);
        const transactions = await res.json();
        renderTransactions(transactions);
    }

    async function fetchInsights() {
        const res = await fetch('/api/insights');
        if (!res.ok) throw new Error(`Insights API error: ${res.status}`);
        const data = await res.json();
        renderInsights(data.insights || []);
    }

    // =========================================================================
    // Render Functions
    // =========================================================================

    function renderSummary(data) {
        // 1. Overview Cards
        const totalIncome = Number(data.totalIncome || 0);
        const totalExpenses = Number(data.totalExpenses || 0);
        const availableBalance = Number(data.availableBalance || 0);
        const remainingBudget = Number(data.remainingBudget || 0);
        const monthlyBudget = Number(data.monthlyBudget || 0);
        const monthExpenses = Number(data.monthExpenses || 0);
        const usagePercentage = Number(data.budgetUsagePercentage || 0);
        const isBudgetSet = Boolean(data.isBudgetSet);

        elements.summaryTotalIncome.textContent = formatCurrency(totalIncome);
        elements.summaryTotalExpenses.textContent = formatCurrency(totalExpenses);
        elements.summaryAvailableBalance.textContent = formatCurrency(availableBalance);
        elements.summaryRemainingBudget.textContent = isBudgetSet ? formatCurrency(remainingBudget) : 'Not Configured';

        if (availableBalance < 0) {
            elements.summaryBalanceSubtext.textContent = 'Expenses exceed recorded income';
            elements.summaryBalanceSubtext.style.color = 'var(--color-danger)';
        } else {
            elements.summaryBalanceSubtext.textContent = 'Income minus expenses';
            elements.summaryBalanceSubtext.style.color = 'var(--color-text-subtle)';
        }

        // 2. Monthly Budget Progress Card
        if (data.currentMonth && elements.currentMonthBadge) {
            const [year, month] = data.currentMonth.split('-');
            const monthDate = new Date(Number(year), Number(month) - 1, 1);
            elements.currentMonthBadge.textContent = monthDate.toLocaleString('en-US', { month: 'long', year: 'numeric' });
        }

        elements.metricMonthlyBudget.textContent = isBudgetSet ? formatCurrency(monthlyBudget) : 'Not Set';
        elements.metricSpentThisMonth.textContent = formatCurrency(monthExpenses);
        elements.metricRemainingBudget.textContent = isBudgetSet ? formatCurrency(remainingBudget) : '--';
        elements.budgetUsagePercentText.textContent = isBudgetSet ? `${usagePercentage.toFixed(2)}%` : '0.00%';

        // Progress bar width & color
        const clampedProgress = Math.min(Math.max(usagePercentage, 0), 100);
        elements.budgetProgressFill.style.width = isBudgetSet ? `${clampedProgress}%` : '0%';

        elements.budgetProgressFill.classList.remove('warning', 'danger');
        elements.budgetAlertBanner.className = 'budget-alert hidden';
        elements.budgetAlertBanner.textContent = '';

        if (isBudgetSet) {
            if (monthExpenses > monthlyBudget) {
                elements.budgetProgressFill.classList.add('danger');
                elements.budgetAlertBanner.className = 'budget-alert danger';
                elements.budgetAlertBanner.textContent = `Budget Alert: Exceeded monthly spending budget by ${formatCurrency(monthExpenses - monthlyBudget)}!`;
            } else if (usagePercentage >= 80) {
                elements.budgetProgressFill.classList.add('warning');
                elements.budgetAlertBanner.className = 'budget-alert warning';
                elements.budgetAlertBanner.textContent = `Budget Warning: Reached ${usagePercentage.toFixed(1)}% of your monthly budget limit.`;
            }
        }

        // Populate update budget input placeholder
        if (isBudgetSet && monthlyBudget > 0) {
            elements.budgetInput.placeholder = `Current: ${monthlyBudget}`;
        }

        // 3. Category Breakdown Visualization
        renderCategoryBreakdown(data.categoryExpenses, monthExpenses);
    }

    function renderCategoryBreakdown(categoryExpenses, totalMonthExpenses) {
        const container = elements.categoryBreakdownContainer;
        if (!container) return;

        if (!categoryExpenses || Object.keys(categoryExpenses).length === 0 || totalMonthExpenses <= 0) {
            container.innerHTML = '<div class="empty-placeholder">No expense data recorded for this month.</div>';
            return;
        }

        // Sort categories by amount descending
        const entries = Object.entries(categoryExpenses)
            .map(([cat, amt]) => ({ category: cat, amount: Number(amt) }))
            .filter(item => item.amount > 0)
            .sort((a, b) => b.amount - a.amount);

        if (entries.length === 0) {
            container.innerHTML = '<div class="empty-placeholder">No expense data recorded for this month.</div>';
            return;
        }

        container.innerHTML = '';
        entries.forEach(item => {
            const percent = totalMonthExpenses > 0 ? (item.amount / totalMonthExpenses) * 100 : 0;

            const row = document.createElement('div');
            row.className = 'category-row';
            row.innerHTML = `
                <div class="category-meta">
                    <span class="category-name">${escapeHtml(item.category)}</span>
                    <span class="category-amount">${formatCurrency(item.amount)} (${percent.toFixed(1)}%)</span>
                </div>
                <div class="category-bar-track">
                    <div class="category-bar-fill" style="width: ${percent.toFixed(1)}%;"></div>
                </div>
            `;
            container.appendChild(row);
        });
    }

    function renderTransactions(transactions) {
        const tbody = elements.transactionsTableBody;
        if (!tbody) return;

        if (!Array.isArray(transactions) || transactions.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center py-4">No transactions recorded yet. Add your first transaction above.</td></tr>';
            if (elements.transactionCountBadge) {
                elements.transactionCountBadge.textContent = '0 Transactions';
            }
            return;
        }

        if (elements.transactionCountBadge) {
            elements.transactionCountBadge.textContent = `${transactions.length} Transaction${transactions.length === 1 ? '' : 's'}`;
        }

        // Render latest first (reverse of chronological order for easy viewing)
        const sorted = [...transactions].reverse();

        tbody.innerHTML = '';
        sorted.forEach(t => {
            const isIncome = t.type === 'INCOME';
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>#${t.id}</td>
                <td>${escapeHtml(t.date || '')}</td>
                <td style="font-weight: 500;">${escapeHtml(t.description || '')}</td>
                <td>${escapeHtml(t.categoryOrSource || '')}</td>
                <td>
                    <span class="type-pill ${isIncome ? 'income' : 'expense'}">${t.type}</span>
                </td>
                <td class="${isIncome ? 'amount-income' : 'amount-expense'}">
                    ${isIncome ? '+' : '-'}${formatCurrency(t.amount)}
                </td>
            `;
            tbody.appendChild(tr);
        });
    }

    function renderInsights(insights) {
        const container = elements.insightsContainer;
        if (!container) return;

        if (!Array.isArray(insights) || insights.length === 0) {
            container.innerHTML = '<div class="empty-placeholder">Record transactions to generate rule-based financial insights.</div>';
            return;
        }

        container.innerHTML = '';
        insights.forEach(text => {
            const card = document.createElement('div');
            card.className = 'insight-card';
            card.innerHTML = `
                <span class="insight-bullet"></span>
                <span>${escapeHtml(text)}</span>
            `;
            container.appendChild(card);
        });
    }

    // =========================================================================
    // Action Handlers
    // =========================================================================

    async function handleAddTransaction(e) {
        e.preventDefault();

        const description = elements.txnDescription.value.trim();
        const amountStr = elements.txnAmount.value.trim();
        const type = elements.txnTypeInput.value.trim();
        const category = elements.txnCategory.value.trim();
        const date = elements.txnDate.value.trim();

        if (!description) {
            showNotification('Please enter a transaction description.', 'danger');
            elements.txnDescription.focus();
            return;
        }

        const amount = Number(amountStr);
        if (isNaN(amount) || amount <= 0) {
            showNotification('Please enter a valid positive monetary amount.', 'danger');
            elements.txnAmount.focus();
            return;
        }

        if (!category) {
            showNotification('Please select a category or source.', 'danger');
            return;
        }

        if (!date) {
            showNotification('Please select a transaction date.', 'danger');
            return;
        }

        const payload = {
            description,
            amount: amount.toFixed(2),
            type,
            category,
            date
        };

        elements.submitTxnBtn.disabled = true;
        elements.submitTxnBtn.textContent = 'Saving...';

        try {
            const res = await fetch('/api/transactions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            const data = await res.json();
            if (!res.ok) {
                throw new Error(data.error || 'Failed to save transaction');
            }

            showNotification(`Successfully added ${type === 'INCOME' ? 'Income' : 'Expense'} of ${formatCurrency(amount)} (#${data.id}).`, 'success');

            // Reset description and amount, keep date and type
            elements.txnDescription.value = '';
            elements.txnAmount.value = '';

            // Refresh entire dashboard
            await fetchAllData();
        } catch (err) {
            showNotification(err.message, 'danger');
        } finally {
            elements.submitTxnBtn.disabled = false;
            elements.submitTxnBtn.textContent = 'Save Transaction';
        }
    }

    async function handleUpdateBudget(e) {
        e.preventDefault();

        const amountStr = elements.budgetInput.value.trim();
        const amount = Number(amountStr);

        if (isNaN(amount) || amount <= 0) {
            showNotification('Please enter a valid positive monthly budget amount.', 'danger');
            elements.budgetInput.focus();
            return;
        }

        elements.saveBudgetBtn.disabled = true;
        elements.saveBudgetBtn.textContent = 'Saving...';

        try {
            const res = await fetch('/api/budget', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ amount: amount.toFixed(2) })
            });

            const data = await res.json();
            if (!res.ok) {
                throw new Error(data.error || 'Failed to update monthly budget');
            }

            showNotification(`Monthly budget updated to ${formatCurrency(amount)} successfully.`, 'success');
            elements.budgetInput.value = '';

            await fetchAllData();
        } catch (err) {
            showNotification(err.message, 'danger');
        } finally {
            elements.saveBudgetBtn.disabled = false;
            elements.saveBudgetBtn.textContent = 'Save Budget';
        }
    }

    async function handleCheckAffordability(e) {
        e.preventDefault();

        const amountStr = elements.plannedPurchaseInput.value.trim();
        const amount = Number(amountStr);

        if (isNaN(amount) || amount <= 0) {
            showNotification('Please enter a valid purchase cost greater than zero.', 'danger');
            elements.plannedPurchaseInput.focus();
            return;
        }

        elements.checkAffordabilityBtn.disabled = true;
        elements.checkAffordabilityBtn.textContent = 'Checking...';

        try {
            const res = await fetch('/api/affordability', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ amount: amount.toFixed(2) })
            });

            const data = await res.json();
            if (!res.ok) {
                throw new Error(data.error || 'Failed to evaluate affordability');
            }

            renderAffordabilityResult(data);
        } catch (err) {
            showNotification(err.message, 'danger');
        } finally {
            elements.checkAffordabilityBtn.disabled = false;
            elements.checkAffordabilityBtn.textContent = 'Evaluate';
        }
    }

    function renderAffordabilityResult(report) {
        elements.affordabilityResultContainer.classList.remove('hidden');

        elements.affordCurrentBalance.textContent = formatCurrency(report.currentBalance);
        elements.affordPurchaseCost.textContent = formatCurrency(report.purchaseCost);
        elements.affordBalanceAfter.textContent = formatCurrency(report.estimatedBalanceAfter);

        if (report.budgetConfigured) {
            elements.affordBudgetAfter.textContent = formatCurrency(report.estimatedRemainingBudgetAfter);
        } else {
            elements.affordBudgetAfter.textContent = 'Budget Not Configured';
        }

        elements.affordabilityExplanationText.textContent = report.assessment || '';

        // Determine status badge
        const currentBalance = Number(report.currentBalance);
        const purchaseCost = Number(report.purchaseCost);
        const remainingBudgetAfter = Number(report.estimatedRemainingBudgetAfter);

        const badge = elements.affordabilityStatusBadge;
        badge.className = 'analysis-status';

        if (currentBalance < purchaseCost) {
            badge.textContent = 'Postpone Purchase';
            badge.classList.add('status-badge-exceeded');
        } else if (report.budgetConfigured && remainingBudgetAfter < 0) {
            badge.textContent = 'Exceeds Budget';
            badge.classList.add('status-badge-caution');
        } else {
            badge.textContent = 'Affordable';
            badge.classList.add('status-badge-ok');
        }
    }

    // =========================================================================
    // UI Notification Helpers
    // =========================================================================

    function showNotification(message, type = 'info') {
        const el = elements.globalNotification;
        if (!el) return;

        if (notificationTimeout) {
            clearTimeout(notificationTimeout);
        }

        elements.notificationMessage.textContent = message;
        el.className = `notification notification-${type}`;
        el.classList.remove('hidden');

        // Auto-dismiss after 6 seconds for success/info
        if (type !== 'danger') {
            notificationTimeout = setTimeout(() => {
                hideNotification();
            }, 6000);
        }
    }

    function hideNotification() {
        if (elements.globalNotification) {
            elements.globalNotification.classList.add('hidden');
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // Start on DOM content loaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

    // =========================================================================
    // Phase 4 — Smart Financial Assistant Chat Widget
    // =========================================================================
    (function initChat() {
        'use strict';

        const API = '/api/assistant';

        // DOM refs
        const chatToggleBtn  = document.getElementById('chatToggleBtn');
        const chatPanel      = document.getElementById('chatPanel');
        const chatCloseBtn   = document.getElementById('chatCloseBtn');
        const chatMessages   = document.getElementById('chatMessages');
        const chatForm       = document.getElementById('chatForm');
        const chatInput      = document.getElementById('chatInput');
        const chatSendBtn    = document.getElementById('chatSendBtn');
        const quickReplies   = document.querySelectorAll('.quick-reply-btn');

        if (!chatToggleBtn || !chatPanel) return;

        let isOpen = false;
        let isWaiting = false;

        // ----- Panel toggle -----
        function openChat() {
            isOpen = true;
            chatPanel.classList.add('is-open');
            chatPanel.setAttribute('aria-hidden', 'false');
            chatToggleBtn.classList.add('is-open');
            chatInput.focus();
            if (chatMessages.children.length === 0) showWelcome();
        }

        function closeChat() {
            isOpen = false;
            chatPanel.classList.remove('is-open');
            chatPanel.setAttribute('aria-hidden', 'true');
            chatToggleBtn.classList.remove('is-open');
        }

        chatToggleBtn.addEventListener('click', () => isOpen ? closeChat() : openChat());
        chatCloseBtn.addEventListener('click', closeChat);

        // Close on Escape
        document.addEventListener('keydown', e => { if (e.key === 'Escape' && isOpen) closeChat(); });

        // ----- Quick reply chips -----
        quickReplies.forEach(btn => {
            btn.addEventListener('click', () => {
                const msg = btn.getAttribute('data-msg');
                if (msg && !isWaiting) sendMessage(msg);
            });
        });

        // ----- Form submit -----
        chatForm.addEventListener('submit', e => {
            e.preventDefault();
            const msg = chatInput.value.trim();
            if (msg && !isWaiting) {
                chatInput.value = '';
                sendMessage(msg);
            }
        });

        // ----- Message flow -----
        function showWelcome() {
            appendBotMessage(
                "Hello! I am your Financial Copilot Assistant.\n\n" +
                "I can answer questions about your balance, budget, spending, savings rate, category breakdown, " +
                "purchase affordability, and more.\n\n" +
                "Try the quick reply chips above, or type any question!"
            );
        }

        async function sendMessage(text) {
            if (!text || !text.trim()) return;
            const trimmedText = text.trim();

            appendUserMessage(trimmedText);
            const typingEl = showTyping();
            isWaiting = true;
            chatSendBtn.disabled = true;

            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 10000); // 10 second timeout

            try {
                const res = await fetch(API, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ message: trimmedText }),
                    signal: controller.signal
                });
                clearTimeout(timeoutId);

                let data = null;
                const contentType = res.headers.get('content-type');
                if (contentType && contentType.includes('application/json')) {
                    try {
                        data = await res.json();
                    } catch (parseErr) {
                        data = null;
                    }
                }

                removeTyping(typingEl);

                if (res.ok && data && data.reply) {
                    appendBotMessage(data.reply);
                } else if (data && data.error) {
                    appendBotMessage(data.error);
                } else if (!res.ok) {
                    appendBotMessage(`Request error (${res.status}): Unable to process your query.`);
                } else {
                    appendBotMessage('Sorry, something went wrong. Please try again.');
                }
            } catch (err) {
                clearTimeout(timeoutId);
                removeTyping(typingEl);
                if (err.name === 'AbortError') {
                    appendBotMessage('The request timed out. Please try asking again.');
                } else {
                    appendBotMessage('Unable to reach the assistant. Please check your network or server connection.');
                }
            } finally {
                isWaiting = false;
                chatSendBtn.disabled = false;
                chatInput.focus();
            }
        }

        function appendUserMessage(text) {
            const wrapper = createMsgWrapper('user');
            const bubble  = document.createElement('div');
            bubble.className = 'chat-bubble';
            bubble.textContent = text;
            wrapper.appendChild(bubble);
            wrapper.appendChild(timeStamp());
            chatMessages.appendChild(wrapper);
            scrollBottom();
        }

        function appendBotMessage(text) {
            const wrapper = createMsgWrapper('bot');
            const bubble  = document.createElement('div');
            bubble.className = 'chat-bubble';
            bubble.textContent = text;
            wrapper.appendChild(bubble);
            wrapper.appendChild(timeStamp());
            chatMessages.appendChild(wrapper);
            scrollBottom();
        }

        function showTyping() {
            const wrapper = createMsgWrapper('bot');
            const indicator = document.createElement('div');
            indicator.className = 'chat-typing';
            indicator.innerHTML = '<span></span><span></span><span></span>';
            wrapper.appendChild(indicator);
            chatMessages.appendChild(wrapper);
            scrollBottom();
            return wrapper;
        }

        function removeTyping(el) {
            if (el && el.parentNode) el.parentNode.removeChild(el);
        }

        function createMsgWrapper(role) {
            const div = document.createElement('div');
            div.className = 'chat-msg ' + role;
            return div;
        }

        function timeStamp() {
            const span = document.createElement('span');
            span.className = 'chat-time';
            const now = new Date();
            span.textContent = now.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
            return span;
        }

        function scrollBottom() {
            requestAnimationFrame(() => {
                chatMessages.scrollTop = chatMessages.scrollHeight;
            });
        }
    })();
