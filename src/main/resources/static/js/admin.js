"use strict";

let currentAuditPage = 0;
const auditPageSize = 15;
let totalAuditPages = 1;

// Toast Notification System
function showToast(message, type = "info") {
    let container = document.getElementById("toast-container");
    if (!container) {
        container = document.createElement("div");
        container.id = "toast-container";
        document.body.appendChild(container);
    }

    const toast = document.createElement("div");
    toast.className = `toast toast-${type}`;

    let icon = "ℹ️";
    if (type === "success") icon = "✓";
    else if (type === "error") icon = "✕";

    toast.innerHTML = `
        <span class="toast-icon">${icon}</span>
        <div class="toast-text">${escapeHtml(message)}</div>
        <button type="button" class="toast-close">&times;</button>
    `;

    toast.querySelector(".toast-close").addEventListener("click", () => {
        toast.classList.add("toast-exit");
        setTimeout(() => toast.remove(), 250);
    });

    container.appendChild(toast);
    setTimeout(() => {
        if (toast.parentElement) {
            toast.classList.add("toast-exit");
            setTimeout(() => toast.remove(), 250);
        }
    }, 4500);
}

function escapeHtml(str) {
    if (!str) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function getCsrfToken() {
    const cookie = document.cookie
        .split("; ")
        .find((item) => item.startsWith("XSRF-TOKEN="));
    return cookie ? decodeURIComponent(cookie.substring("XSRF-TOKEN=".length)) : null;
}

// Session check
async function checkAdminSession() {
    try {
        const res = await fetch("/api/auth/me", {
            credentials: "same-origin",
            headers: { "Accept": "application/json" }
        });

        if (res.status === 401 || res.redirected) {
            window.location.href = "/login.html";
            return null;
        }

        if (!res.ok) {
            throw new Error("Could not verify administrative session.");
        }

        const user = await res.json();
        const roles = user.roles || (user.role ? [user.role] : []);
        const isAdmin = roles.includes("ROLE_ADMIN") || roles.includes("ADMIN") || user.role === "ADMIN";

        if (!isAdmin) {
            showToast("Administrative privileges required. Redirecting to workspace...", "error");
            setTimeout(() => { window.location.href = "/dashboard.html"; }, 1000);
            return null;
        }

        const info = document.getElementById("admin-user-info");
        if (info) {
            info.textContent = `Administrator session active: ${user.fullName || user.email} (${user.email})`;
        }

        return user;
    } catch (err) {
        console.error(err);
        window.location.href = "/login.html";
        return null;
    }
}

// Tab Switching
document.querySelectorAll(".admin-tab").forEach((tab) => {
    tab.addEventListener("click", () => {
        document.querySelectorAll(".admin-tab").forEach((t) => t.classList.remove("active"));
        tab.classList.add("active");

        const targetTab = tab.dataset.tab;
        document.querySelectorAll(".admin-tab-content").forEach((content) => {
            content.style.display = content.id === targetTab ? "block" : "none";
        });

        if (targetTab === "tab-users") {
            loadUsers();
        } else if (targetTab === "tab-audit") {
            loadAuditLogs(currentAuditPage);
        }
    });
});

// Load Stats Summary
async function loadStatsSummary() {
    try {
        // Users count
        const usersRes = await fetch("/api/admin/users", { credentials: "same-origin" });
        if (usersRes.ok) {
            const users = await usersRes.json();
            if (Array.isArray(users)) {
                document.getElementById("stat-total-users").textContent = users.length;
            }
        }

        // Documents count
        const docsRes = await fetch("/api/documents", { credentials: "same-origin" });
        if (docsRes.ok) {
            const docs = await docsRes.json();
            if (Array.isArray(docs)) {
                document.getElementById("stat-total-docs").textContent = docs.length;
            }
        }

        // Audit events count
        const auditRes = await fetch("/api/admin/audit?page=0&size=1", { credentials: "same-origin" });
        if (auditRes.ok) {
            const auditData = await auditRes.json();
            if (auditData && typeof auditData.totalElements === "number") {
                document.getElementById("stat-total-audit").textContent = auditData.totalElements;
            }
        }
    } catch (err) {
        console.warn("Could not load full stats overview:", err);
    }
}

// Load Audit Logs
async function loadAuditLogs(page = 0) {
    const tbody = document.getElementById("audit-table-body");
    const pageInfo = document.getElementById("audit-pagination-info");
    const prevBtn = document.getElementById("btn-audit-prev");
    const nextBtn = document.getElementById("btn-audit-next");

    tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: #94a3b8; padding: 30px;">Loading cryptographic audit stream...</td></tr>`;

    try {
        const res = await fetch(`/api/admin/audit?page=${page}&size=${auditPageSize}`, {
            credentials: "same-origin",
            headers: { "Accept": "application/json" }
        });

        if (!res.ok) {
            throw new Error(`Failed to load audit history (HTTP ${res.status}).`);
        }

        const data = await res.json();
        const logs = data.content || [];
        totalAuditPages = data.totalPages || 1;
        currentAuditPage = data.number || 0;

        pageInfo.textContent = `Page ${currentAuditPage + 1} of ${Math.max(totalAuditPages, 1)} (${data.totalElements || 0} total events)`;
        prevBtn.disabled = currentAuditPage <= 0;
        nextBtn.disabled = currentAuditPage >= totalAuditPages - 1;

        if (logs.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: #94a3b8; padding: 30px;">No audit events recorded yet.</td></tr>`;
            return;
        }

        tbody.innerHTML = "";
        logs.forEach((log) => {
            const tr = document.createElement("tr");

            const isSuccess = log.outcome === "SUCCESS";
            const outcomeClass = isSuccess ? "badge-success" : "badge-denied";
            const formattedTime = log.eventTime ? new Date(log.eventTime).toLocaleString() : "Unknown";

            tr.innerHTML = `
                <td style="font-weight: 600; color: #94a3b8; font-family: monospace;">#${log.id}</td>
                <td style="white-space: nowrap; font-size: 12px;">${escapeHtml(formattedTime)}</td>
                <td><strong style="color: #f1f5f9;">${escapeHtml(log.action || "UNKNOWN")}</strong></td>
                <td><span class="badge-status ${outcomeClass}">${escapeHtml(log.outcome || "N/A")}</span></td>
                <td>
                    <div style="font-weight: 600; color: #f1f5f9;">${escapeHtml(log.documentTitle || "(Document #" + (log.documentId || "N/A") + ")")}</div>
                    <div style="font-size: 11px; color: #64748b;">Doc ID: #${log.documentId || "N/A"}</div>
                </td>
                <td>
                    <div style="font-weight: 600; color: #f1f5f9;">${escapeHtml(log.userName || log.userEmail || "System")}</div>
                    <div style="font-size: 11px; color: #64748b;">${escapeHtml(log.userEmail || "")} (User #${log.userId || "N/A"})</div>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: #fca5a5; padding: 24px;">${escapeHtml(err.message)}</td></tr>`;
        showToast(err.message, "error");
    }
}

// Audit Pagination
document.getElementById("btn-audit-prev").addEventListener("click", () => {
    if (currentAuditPage > 0) {
        loadAuditLogs(currentAuditPage - 1);
    }
});

document.getElementById("btn-audit-next").addEventListener("click", () => {
    if (currentAuditPage < totalAuditPages - 1) {
        loadAuditLogs(currentAuditPage + 1);
    }
});

document.getElementById("btn-refresh-audit").addEventListener("click", () => {
    loadAuditLogs(currentAuditPage);
});

// Verify Chain Integrity
document.getElementById("btn-verify-chain").addEventListener("click", async () => {
    const btn = document.getElementById("btn-verify-chain");
    const shield = document.getElementById("integrity-shield");
    const statusText = document.getElementById("integrity-status-text");
    const detailText = document.getElementById("integrity-detail-text");
    const statChain = document.getElementById("stat-chain-status");

    btn.disabled = true;
    btn.textContent = "Verifying HMAC Ledger...";

    try {
        const res = await fetch("/api/admin/audit/integrity", {
            credentials: "same-origin",
            headers: { "Accept": "application/json" }
        });

        const data = await res.json();

        if (res.ok && data.status === "OK") {
            shield.className = "integrity-icon-shield";
            shield.textContent = "🛡️";
            statusText.textContent = "Audit Chain Verified: Intact & Tamper-Evident";
            statusText.style.color = "#4ffe9c";
            detailText.textContent = `${data.message} Verified at ${new Date().toLocaleTimeString()}. Sequential cryptographic hashes match blockchain head.`;
            if (statChain) {
                statChain.textContent = "Verified ✓";
                statChain.style.color = "#4ffe9c";
            }
            showToast("HMAC Audit Chain verified successfully: Tamper-evident ledger intact.", "success");
        } else {
            shield.className = "integrity-icon-shield failed";
            shield.textContent = "⚠️";
            statusText.textContent = "ALERT: Audit Integrity Verification Failed!";
            statusText.style.color = "#ef4444";
            detailText.textContent = `Discrepancy detected: ${data.message || "Hash chain mismatch or record alteration detected."}`;
            if (statChain) {
                statChain.textContent = "Mismatch ✕";
                statChain.style.color = "#ef4444";
            }
            showToast("Audit chain mismatch detected!", "error");
        }
    } catch (err) {
        shield.className = "integrity-icon-shield failed";
        statusText.textContent = "Verification Request Failed";
        detailText.textContent = err.message;
        showToast(err.message, "error");
    } finally {
        btn.disabled = false;
        btn.textContent = "Re-Verify Chain Integrity";
    }
});

// Load Users
async function loadUsers() {
    const tbody = document.getElementById("users-table-body");
    tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #94a3b8; padding: 30px;">Loading user directory...</td></tr>`;

    try {
        const res = await fetch("/api/admin/users", {
            credentials: "same-origin",
            headers: { "Accept": "application/json" }
        });

        if (!res.ok) {
            throw new Error(`Failed to load users (HTTP ${res.status}).`);
        }

        const users = await res.json();
        if (!Array.isArray(users) || users.length === 0) {
            tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #94a3b8; padding: 30px;">No registered users found.</td></tr>`;
            return;
        }

        tbody.innerHTML = "";
        users.forEach((u) => {
            const tr = document.createElement("tr");

            const isEnabled = u.enabled !== false;
            const statusClass = isEnabled ? "badge-success" : "badge-failed";
            const statusLabel = isEnabled ? "Active" : "Disabled";
            const created = u.createdAt ? new Date(u.createdAt).toLocaleDateString() : "—";

            tr.innerHTML = `
                <td style="color: #94a3b8; font-family: monospace;">#${u.id}</td>
                <td><strong style="color: #f1f5f9;">${escapeHtml(u.fullName || "—")}</strong></td>
                <td>${escapeHtml(u.email)}</td>
                <td><span class="badge-status badge-role">${escapeHtml(u.role)}</span></td>
                <td><span class="badge-status ${statusClass}">${statusLabel}</span></td>
                <td>${created}</td>
                <td>
                    <div style="display: flex; gap: 8px; align-items: center;">
                        <button class="btn btn-sm ${isEnabled ? 'btn-danger' : 'btn-primary'}"
                                style="padding: 4px 10px; font-size: 11px;"
                                data-user-id="${u.id}" data-action="toggle-enabled" data-enabled="${isEnabled}">
                            ${isEnabled ? 'Disable' : 'Enable'}
                        </button>
                        <button class="btn btn-secondary btn-sm"
                                style="padding: 4px 10px; font-size: 11px;"
                                data-user-id="${u.id}" data-action="change-role" data-name="${escapeHtml(u.fullName || u.email)}" data-role="${u.role}">
                            Change Role
                        </button>
                    </div>
                </td>
            `;

            tr.querySelector('[data-action="toggle-enabled"]').addEventListener("click", () => {
                toggleUserEnabled(u.id, isEnabled);
            });

            tr.querySelector('[data-action="change-role"]').addEventListener("click", () => {
                openRoleModal(u.id, u.fullName || u.email, u.role);
            });

            tbody.appendChild(tr);
        });
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #fca5a5; padding: 24px;">${escapeHtml(err.message)}</td></tr>`;
        showToast(err.message, "error");
    }
}

// Toggle User Enabled
async function toggleUserEnabled(userId, currentEnabled) {
    const actionName = currentEnabled ? "disable" : "enable";
    if (!confirm(`Are you sure you want to ${actionName} this user account?`)) {
        return;
    }

    const csrf = getCsrfToken();
    try {
        const res = await fetch(`/api/admin/users/${userId}/enabled`, {
            method: "PATCH",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": csrf || ""
            },
            body: JSON.stringify({ enabled: !currentEnabled })
        });

        if (!res.ok) {
            throw new Error(`Failed to update user status (HTTP ${res.status}).`);
        }

        showToast(`User account ${!currentEnabled ? 'enabled' : 'disabled'} successfully.`, "success");
        await loadUsers();
    } catch (err) {
        showToast(err.message, "error");
    }
}

// Role Change Modal
const roleModal = document.getElementById("change-role-modal");
const roleUserIdInput = document.getElementById("role-user-id");
const roleUserDesc = document.getElementById("role-user-desc");
const targetRoleSelect = document.getElementById("target-user-role");

function openRoleModal(userId, name, currentRole) {
    roleUserIdInput.value = userId;
    roleUserDesc.textContent = `Modifying role for ${name} (currently ${currentRole}).`;
    targetRoleSelect.value = currentRole;
    roleModal.classList.add("open");
}

function closeRoleModal() {
    roleModal.classList.remove("open");
}

document.getElementById("btn-close-role-modal").addEventListener("click", closeRoleModal);
document.getElementById("btn-cancel-role-modal").addEventListener("click", closeRoleModal);

document.getElementById("change-role-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const userId = roleUserIdInput.value;
    const newRole = targetRoleSelect.value;
    const csrf = getCsrfToken();

    try {
        const res = await fetch(`/api/admin/users/${userId}/role`, {
            method: "PATCH",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": csrf || ""
            },
            body: JSON.stringify({ role: newRole })
        });

        if (!res.ok) {
            throw new Error(`Role update failed (HTTP ${res.status}).`);
        }

        closeRoleModal();
        showToast(`User role updated to ${newRole}.`, "success");
        await loadUsers();
    } catch (err) {
        showToast(err.message, "error");
    }
});

// Create User Modal
const createUserModal = document.getElementById("create-user-modal");
const createUserForm = document.getElementById("create-user-form");
const modalError = document.getElementById("modal-error-msg");

document.getElementById("btn-open-create-user").addEventListener("click", () => {
    createUserForm.reset();
    modalError.style.display = "none";
    createUserModal.classList.add("open");
});

function closeCreateUserModal() {
    createUserModal.classList.remove("open");
}

document.getElementById("btn-close-modal").addEventListener("click", closeCreateUserModal);
document.getElementById("btn-cancel-modal").addEventListener("click", closeCreateUserModal);

createUserForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    modalError.style.display = "none";

    const fullName = document.getElementById("new-user-name").value.trim();
    const email = document.getElementById("new-user-email").value.trim();
    const password = document.getElementById("new-user-password").value;
    const role = document.getElementById("new-user-role").value;

    const csrf = getCsrfToken();
    const submitBtn = document.getElementById("btn-submit-user");
    submitBtn.disabled = true;

    try {
        const res = await fetch("/api/admin/users", {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": csrf || ""
            },
            body: JSON.stringify({ fullName, email, password, role })
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || `Creation failed (HTTP ${res.status}).`);
        }

        closeCreateUserModal();
        showToast(`User ${email} created with role ${role}.`, "success");
        await loadUsers();
        await loadStatsSummary();
    } catch (err) {
        modalError.textContent = err.message;
        modalError.style.display = "block";
    } finally {
        submitBtn.disabled = false;
    }
});

// Logout Handler
document.getElementById("nav-logout-btn").addEventListener("click", async () => {
    const csrf = getCsrfToken();
    try {
        await fetch("/api/auth/logout", {
            method: "POST",
            credentials: "same-origin",
            headers: { "X-XSRF-TOKEN": csrf || "" }
        });
    } finally {
        window.location.href = "/login.html?logout";
    }
});

// Initial boot
(async function init() {
    const admin = await checkAdminSession();
    if (admin) {
        loadStatsSummary();
        loadAuditLogs(0);
    }
})();
