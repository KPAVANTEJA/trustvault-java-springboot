"use strict";

let currentUser = null;

// Toast Notification System
function showToast(message, type = "info") {
    const container = document.getElementById("toast-container") || createToastContainer();
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

function createToastContainer() {
    const div = document.createElement("div");
    div.id = "toast-container";
    document.body.appendChild(div);
    return div;
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
    const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : "";
}

function formatBytes(bytes) {
    if (bytes === 0) return "0 Bytes";
    if (!bytes) return "—";
    const k = 1024;
    const sizes = ["Bytes", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
}

const welcomeEl = document.getElementById("welcome");
const badgeContainer = document.getElementById("user-role-badge-container");
const uploadPanel = document.getElementById("upload-panel");
const uploadForm = document.getElementById("upload-form");
const uploadMessage = document.getElementById("upload-message");
const documentList = document.getElementById("document-list");
const listMessage = document.getElementById("list-message");
const documentSearch = document.getElementById("document-search");
const navAdminLink = document.getElementById("nav-admin-link");
const navLogoutBtn = document.getElementById("nav-logout-btn");

// Session Check
async function checkSession() {
    try {
        const response = await fetch("/api/auth/me", {
            credentials: "same-origin",
            headers: { "Accept": "application/json" }
        });

        if (response.status === 401 || response.redirected) {
            window.location.href = "/login.html";
            return null;
        }

        if (!response.ok) {
            throw new Error("Could not verify session.");
        }

        const user = await response.json();
        currentUser = user;
        const roles = user.roles || (user.role ? [user.role] : []);
        const primaryRole = roles.includes("ROLE_ADMIN") || user.role === "ADMIN" ? "ADMIN"
                          : roles.includes("ROLE_OWNER") || user.role === "OWNER" ? "OWNER"
                          : "VIEWER";
        currentUser.role = primaryRole;

        const displayName = user.fullName || user.email;
        welcomeEl.textContent = `Authenticated session: ${displayName} (${user.email})`;

        badgeContainer.innerHTML = `
            <span class="user-badge" style="font-weight: 700; font-size: 12px;">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                </svg>
                Role: ${primaryRole}
            </span>
        `;

        // Configure upload panel visibility
        if (primaryRole === "VIEWER") {
            if (uploadPanel) uploadPanel.style.display = "none";
        } else {
            if (uploadPanel) uploadPanel.style.display = "block";
        }

        // Configure Admin Console link visibility
        if (primaryRole === "ADMIN") {
            if (navAdminLink) navAdminLink.style.display = "inline-block";
        }

        return user;
    } catch (error) {
        console.error("Session check failed:", error);
        window.location.href = "/login.html";
        return null;
    }
}

// Drag & Drop Setup
const dropzone = document.getElementById("upload-dropzone");
const fileInput = document.getElementById("file");
const dropzoneFileName = document.getElementById("dropzone-file-name");
const fileNameDisplay = document.getElementById("file-name-display");
const fileSizeDisplay = document.getElementById("file-size-display");

if (dropzone && fileInput) {
    ["dragenter", "dragover"].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.add("drag-active");
        }, false);
    });

    ["dragleave", "drop"].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropzone.classList.remove("drag-active");
        }, false);
    });

    dropzone.addEventListener("drop", (e) => {
        const dt = e.dataTransfer;
        const files = dt.files;
        if (files.length > 0) {
            fileInput.files = files;
            updateDropzoneDisplay(files[0]);
        }
    });

    fileInput.addEventListener("change", () => {
        if (fileInput.files.length > 0) {
            updateDropzoneDisplay(fileInput.files[0]);
        }
    });
}

function updateDropzoneDisplay(file) {
    if (!dropzoneFileName) return;
    fileNameDisplay.textContent = file.name;
    fileSizeDisplay.textContent = `(${formatBytes(file.size)})`;
    dropzoneFileName.classList.add("visible");
}

// Skeleton Loader
function renderSkeletons() {
    documentList.innerHTML = `
        <div class="skeleton-card" style="background: #0f1929; border: 1px solid rgba(255,255,255,0.06); border-radius: 14px; padding: 22px;">
            <div class="skeleton skeleton-line w-40" style="height: 18px; margin-bottom: 8px;"></div>
            <div class="skeleton skeleton-line w-60 short" style="margin-bottom: 14px;"></div>
            <div class="skeleton skeleton-line" style="height: 36px; margin-bottom: 12px;"></div>
            <div class="skeleton skeleton-line w-80 short"></div>
        </div>
        <div class="skeleton-card" style="background: #0f1929; border: 1px solid rgba(255,255,255,0.06); border-radius: 14px; padding: 22px;">
            <div class="skeleton skeleton-line w-40" style="height: 18px; margin-bottom: 8px;"></div>
            <div class="skeleton skeleton-line w-60 short" style="margin-bottom: 14px;"></div>
            <div class="skeleton skeleton-line" style="height: 36px; margin-bottom: 12px;"></div>
            <div class="skeleton skeleton-line w-80 short"></div>
        </div>
    `;
}

// Document Card Generator
function createDocumentCard(doc) {
    const card = document.createElement("div");
    card.className = "document-card";

    const filename = doc.filename || doc.originalFilename || "document";
    const uploadDate = doc.createdAt ? new Date(doc.createdAt).toLocaleDateString() : "Recently";
    const category = doc.category || "Uncategorized";
    const formattedSize = formatBytes(doc.sizeBytes);
    const ownerInfo = doc.ownerName ? `${doc.ownerName} (${doc.ownerEmail || ""})` : (doc.ownerEmail || "Vault Owner");

    const topRow = document.createElement("div");
    topRow.className = "document-card-top";

    const titleGroup = document.createElement("div");
    titleGroup.style.flex = "1";
    titleGroup.style.minWidth = "0";

    const titleEl = document.createElement("h3");
    titleEl.style.fontSize = "16px";
    titleEl.style.fontWeight = "700";
    titleEl.style.color = "#f1f5f9";
    titleEl.style.marginBottom = "4px";
    titleEl.textContent = doc.title || "Untitled Document";

    const filenameEl = document.createElement("div");
    filenameEl.style.fontSize = "13px";
    filenameEl.style.color = "#94a3b8";
    filenameEl.textContent = filename;
    titleGroup.append(titleEl, filenameEl);

    const badges = document.createElement("div");
    badges.style.display = "flex";
    badges.style.gap = "8px";
    badges.style.alignItems = "center";
    badges.innerHTML = `
        <span class="category-tag cat-${category}">${escapeHtml(category)}</span>
        <span class="badge badge-gray" style="font-size: 11px;">#${doc.id}</span>
    `;

    topRow.append(titleGroup, badges);

    // Meta Bar
    const metaBar = document.createElement("div");
    metaBar.className = "doc-meta-bar";
    metaBar.innerHTML = `
        <div class="doc-meta-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline>
            </svg>
            <span>${uploadDate}</span>
        </div>
        <div class="doc-meta-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
            </svg>
            <span>${formattedSize}</span>
        </div>
        <div class="doc-meta-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle>
            </svg>
            <span>Owner: <strong>${escapeHtml(ownerInfo)}</strong></span>
        </div>
    `;

    // Action Bar
    const actionBar = document.createElement("div");
    actionBar.className = "doc-action-bar";

    // View Button
    const viewBtn = document.createElement("button");
    viewBtn.type = "button";
    viewBtn.className = "btn btn-secondary btn-sm";
    viewBtn.textContent = "View";
    viewBtn.addEventListener("click", () => handleViewDocument(doc, viewBtn));
    actionBar.appendChild(viewBtn);

    // 1. Download Button
    const downloadBtn = document.createElement("button");
    downloadBtn.type = "button";
    downloadBtn.className = "btn btn-primary btn-sm";
    downloadBtn.innerHTML = `
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
            <polyline points="7 10 12 15 17 10"></polyline>
            <line x1="12" y1="15" x2="12" y2="3"></line>
        </svg>
        Download
    `;
    downloadBtn.addEventListener("click", () => handleDownload(doc, downloadBtn));
    actionBar.appendChild(downloadBtn);

    const isOwnerOrAdmin = doc.isOwner === true || (currentUser && currentUser.role === "ADMIN");
    const canEdit = isOwnerOrAdmin || doc.canEdit === true || doc.permissionType === "EDIT";

    // 2. Edit Metadata Button (Available to owners, admins, or users granted EDIT permission)
    if (canEdit) {
        const editBtn = document.createElement("button");
        editBtn.type = "button";
        editBtn.className = "btn btn-secondary btn-sm";
        editBtn.innerHTML = `
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 20h9"></path>
                <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path>
            </svg>
            Edit Info
        `;
        editBtn.addEventListener("click", () => openEditMetadataModal(doc));
        actionBar.appendChild(editBtn);

        // 3. Replace Content Button
        const replaceBtn = document.createElement("button");
        replaceBtn.type = "button";
        replaceBtn.className = "btn btn-secondary btn-sm";
        replaceBtn.innerHTML = `
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="1 4 1 10 7 10"></polyline>
                <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"></path>
            </svg>
            Replace File
        `;
        replaceBtn.addEventListener("click", () => openReplaceModal(doc));
        actionBar.appendChild(replaceBtn);
    }

    // 4. Delete Button (Owner or Admin only)
    if (isOwnerOrAdmin) {
        const deleteBtn = document.createElement("button");
        deleteBtn.type = "button";
        deleteBtn.className = "btn btn-danger btn-sm";
        deleteBtn.innerHTML = `
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"></polyline>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
            </svg>
            Delete
        `;
        deleteBtn.addEventListener("click", () => handleDeleteDocument(doc));
        actionBar.appendChild(deleteBtn);
    }

    card.append(topRow, metaBar, actionBar);

    // 5. Sharing & Permissions Drawer (Owner or Admin only)
    if (isOwnerOrAdmin) {
        const permissionsDrawer = document.createElement("div");
        permissionsDrawer.className = "perms-drawer";

        const shareHeader = document.createElement("button");
        shareHeader.type = "button";
        shareHeader.className = "perms-toggle";
        shareHeader.innerHTML = `
            <span style="display: flex; align-items: center; gap: 7px; color: #cbd5e1;">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="18" cy="5" r="3"></circle><circle cx="6" cy="12" r="3"></circle><circle cx="18" cy="19" r="3"></circle>
                    <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"></line><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"></line>
                </svg>
                Manage Access Permissions
            </span>
            <span class="perms-toggle-arrow">▾</span>
        `;

        const drawerBody = document.createElement("div");
        drawerBody.className = "perms-body";

        const innerBody = document.createElement("div");
        innerBody.className = "perms-inner";

        shareHeader.addEventListener("click", () => {
            const isOpen = shareHeader.classList.toggle("open");
            drawerBody.classList.toggle("open", isOpen);
            if (isOpen) {
                loadDocumentPermissions(doc.id, permissionsListEl);
            }
        });

        // Grant Form
        const grantForm = document.createElement("div");
        grantForm.className = "grant-form-grid";

        const recipientInput = document.createElement("input");
        recipientInput.type = "email";
        recipientInput.placeholder = "Recipient user email...";
        recipientInput.className = "form-control";
        recipientInput.style.padding = "8px 12px";

        const permissionSelect = document.createElement("select");
        permissionSelect.className = "form-control";
        permissionSelect.style.padding = "8px 12px";
        permissionSelect.innerHTML = `
            <option value="VIEW">View Only</option>
            <option value="EDIT">View & Edit</option>
        `;

        const grantBtn = document.createElement("button");
        grantBtn.type = "button";
        grantBtn.className = "btn btn-primary btn-sm";
        grantBtn.textContent = "Grant Access";
        grantBtn.addEventListener("click", async () => {
            const email = recipientInput.value.trim();
            if (!email) {
                showToast("Enter recipient's registered email address.", "error");
                return;
            }
            await handleGrantPermission(doc.id, email, permissionSelect.value, recipientInput, permissionsListEl);
        });

        grantForm.append(recipientInput, permissionSelect, grantBtn);

        const permissionsListEl = document.createElement("div");
        permissionsListEl.className = "perm-list";
        permissionsListEl.innerHTML = `<span class="muted" style="font-size: 12px; color: #64748b;">Loading granted users...</span>`;

        innerBody.append(grantForm, permissionsListEl);
        drawerBody.appendChild(innerBody);
        permissionsDrawer.append(shareHeader, drawerBody);
        card.appendChild(permissionsDrawer);
    }

    return card;
}

// View Handler
async function handleViewDocument(doc, btn) {
    // Open the tab immediately to avoid browser popup blockers.
    const previewWindow = window.open("about:blank", "_blank");

    if (!previewWindow) {
        showToast("Allow pop-ups to open the document preview.", "error");
        return;
    }

    previewWindow.opener = null;

    const originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = "Opening...";

    try {
        const response = await fetch(
            `/api/documents/${encodeURIComponent(doc.id)}/view`,
            {
                method: "GET",
                credentials: "same-origin",
                headers: { "Accept": "application/pdf, image/*, text/plain" }
            }
        );

        if (response.status === 401 || response.redirected) {
            previewWindow.close();
            window.location.href = "/login.html";
            return;
        }

        if (response.status === 415) {
            previewWindow.close();
            showToast(
                "Preview is unavailable for this file type. Use Download instead.",
                "error"
            );
            return;
        }

        if (!response.ok) {
            throw new Error(
                response.status === 404
                    ? "Document not found or access denied."
                    : `Preview failed (HTTP ${response.status}).`
            );
        }

        const blob = await response.blob();
        const objectUrl = URL.createObjectURL(blob);

        if (previewWindow.closed) {
            URL.revokeObjectURL(objectUrl);
            return;
        }

        previewWindow.document.title =
            `TrustVault Preview - ${doc.title || "Document"}`;

        previewWindow.location.replace(objectUrl);

        // Release the temporary file URL when the preview tab is closed.
        const cleanupId = window.setInterval(() => {
            if (previewWindow.closed) {
                URL.revokeObjectURL(objectUrl);
                window.clearInterval(cleanupId);
            }
        }, 1000);

        showToast(`Opened "${doc.title}" for viewing.`, "success");
    } catch (error) {
        if (!previewWindow.closed) {
            previewWindow.close();
        }

        showToast(error.message || "Unable to preview this document.", "error");
    } finally {
        btn.disabled = false;
        btn.textContent = originalText;
    }
}

// Download Handler
async function handleDownload(doc, btn) {
    btn.disabled = true;
    const originalText = btn.innerHTML;
    btn.textContent = "Downloading...";
    listMessage.textContent = "";

    try {
        const response = await fetch(`/api/documents/${encodeURIComponent(doc.id)}/download`, {
            method: "GET",
            credentials: "same-origin"
        });

        if (response.status === 401 || response.redirected) {
            window.location.href = "/login.html";
            return;
        }

        if (!response.ok) {
            throw new Error(
                response.status === 404
                    ? "Document not found or access denied."
                    : `Download failed (HTTP ${response.status}).`
            );
        }

        const blob = await response.blob();
        const objectUrl = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = objectUrl;
        link.download = doc.filename || doc.originalFilename || "document";

        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
        showToast(`Downloaded "${doc.title}"`, "success");
    } catch (error) {
        showToast(error.message, "error");
    } finally {
        btn.disabled = false;
        btn.innerHTML = originalText;
    }
}

// Delete Document Handler
async function handleDeleteDocument(doc) {
    if (!confirm(`Are you sure you want to delete "${doc.title}"? This soft-deletes the document and immediately revokes all active links.`)) {
        return;
    }

    const csrf = getCsrfToken();
    try {
        const res = await fetch(`/api/documents/${doc.id}`, {
            method: "DELETE",
            credentials: "same-origin",
            headers: { "X-XSRF-TOKEN": csrf || "" }
        });

        if (!res.ok) {
            throw new Error(`Deletion failed (HTTP ${res.status}).`);
        }

        showToast(`Document "${doc.title}" deleted.`, "success");
        await loadDocuments(documentSearch.value);
    } catch (err) {
        showToast(err.message, "error");
    }
}

// Load Document Permissions
async function loadDocumentPermissions(docId, listEl) {
    listEl.innerHTML = `<span class="muted" style="font-size: 12px; color: #64748b;">Loading grants...</span>`;

    try {
        const res = await fetch(`/api/documents/${encodeURIComponent(docId)}/permissions`, {
            credentials: "same-origin",
            headers: { "Accept": "application/json" }
        });

        if (!res.ok) {
            listEl.innerHTML = `<span class="muted" style="font-size: 12px; color: #fca5a5;">Permissions unavailable (HTTP ${res.status}).</span>`;
            return;
        }

        const grants = await res.json();
        if (!Array.isArray(grants) || grants.length === 0) {
            listEl.innerHTML = `<span class="muted" style="font-size: 12px; color: #64748b;">Not shared with anyone yet.</span>`;
            return;
        }

        listEl.innerHTML = "";
        grants.forEach((grant) => {
            const chip = document.createElement("div");
            chip.className = "perm-chip";

            const userTitle = grant.fullName ? `${grant.fullName} (${grant.email})` : grant.email;
            chip.innerHTML = `
                <div>
                    <strong>${escapeHtml(userTitle)}</strong>
                    <span class="badge badge-green" style="margin-left: 8px;">${grant.permissionType}</span>
                </div>
            `;

            const revokeBtn = document.createElement("button");
            revokeBtn.type = "button";
            revokeBtn.className = "btn btn-danger btn-sm";
            revokeBtn.style.padding = "3px 8px";
            revokeBtn.style.fontSize = "11px";
            revokeBtn.textContent = "Revoke";
            revokeBtn.addEventListener("click", () => handleRevokePermission(docId, grant.recipientId, grant.email, listEl));

            chip.appendChild(revokeBtn);
            listEl.appendChild(chip);
        });
    } catch {
        listEl.innerHTML = `<span class="muted" style="font-size: 12px; color: #fca5a5;">Could not load shared permissions.</span>`;
    }
}

// Grant Permission Handler
async function handleGrantPermission(docId, email, permissionType, inputEl, listEl) {
    const csrf = getCsrfToken();
    try {
        const res = await fetch(`/api/documents/${encodeURIComponent(docId)}/permissions`, {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": csrf || ""
            },
            body: JSON.stringify({ email, permissionType })
        });

        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.message || `Grant failed (HTTP ${res.status}).`);
        }

        inputEl.value = "";
        showToast(`Granted ${permissionType} access to ${email}.`, "success");
        await loadDocumentPermissions(docId, listEl);
    } catch (err) {
        showToast(err.message, "error");
    }
}

// Revoke Permission Handler
async function handleRevokePermission(docId, recipientId, email, listEl) {
    if (!confirm(`Revoke document access for ${email}?`)) return;

    const csrf = getCsrfToken();
    try {
        const res = await fetch(`/api/documents/${encodeURIComponent(docId)}/permissions/${encodeURIComponent(recipientId)}`, {
            method: "DELETE",
            credentials: "same-origin",
            headers: { "X-XSRF-TOKEN": csrf || "" }
        });

        if (!res.ok) {
            throw new Error(`Revocation failed (HTTP ${res.status}).`);
        }

        showToast(`Revoked access for ${email}.`, "success");
        await loadDocumentPermissions(docId, listEl);
    } catch (err) {
        showToast(err.message, "error");
    }
}

// Edit Metadata Modal Handling
const editModal = document.getElementById("edit-metadata-modal");
const editForm = document.getElementById("edit-metadata-form");
const editDocIdInput = document.getElementById("edit-doc-id");
const editDocTitleInput = document.getElementById("edit-doc-title");
const editDocCategoryInput = document.getElementById("edit-doc-category");
const editModalError = document.getElementById("edit-modal-error");

function openEditMetadataModal(doc) {
    editDocIdInput.value = doc.id;
    editDocTitleInput.value = doc.title || "";
    editDocCategoryInput.value = doc.category || "Uncategorized";
    editModalError.style.display = "none";
    editModal.classList.add("open");
}

function closeEditModal() {
    editModal.classList.remove("open");
}

document.getElementById("btn-close-edit-modal").addEventListener("click", closeEditModal);
document.getElementById("btn-cancel-edit-modal").addEventListener("click", closeEditModal);

editForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const docId = editDocIdInput.value;
    const title = editDocTitleInput.value.trim();
    const category = editDocCategoryInput.value;
    const csrf = getCsrfToken();

    try {
        // Uses PUT (with fallback PATCH supported by backend)
        const res = await fetch(`/api/documents/${docId}`, {
            method: "PUT",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "X-XSRF-TOKEN": csrf || ""
            },
            body: JSON.stringify({ title, category })
        });

        if (!res.ok) {
            const txt = await res.text();
            throw new Error(txt || `Failed to update metadata (HTTP ${res.status}).`);
        }

        closeEditModal();
        showToast("Document metadata updated successfully.", "success");
        await loadDocuments(documentSearch.value);
    } catch (err) {
        editModalError.textContent = err.message;
        editModalError.style.display = "block";
    }
});

// Replace Content Modal Handling
const replaceModal = document.getElementById("replace-content-modal");
const replaceForm = document.getElementById("replace-content-form");
const replaceDocIdInput = document.getElementById("replace-doc-id");
const replaceFileInput = document.getElementById("replace-doc-file");
const replaceModalError = document.getElementById("replace-modal-error");

function openReplaceModal(doc) {
    replaceDocIdInput.value = doc.id;
    replaceFileInput.value = "";
    replaceModalError.style.display = "none";
    replaceModal.classList.add("open");
}

function closeReplaceModal() {
    replaceModal.classList.remove("open");
}

document.getElementById("btn-close-replace-modal").addEventListener("click", closeReplaceModal);
document.getElementById("btn-cancel-replace-modal").addEventListener("click", closeReplaceModal);

replaceForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    const docId = replaceDocIdInput.value;
    const file = replaceFileInput.files[0];
    if (!file) return;

    const csrf = getCsrfToken();
    const formData = new FormData();
    formData.append("file", file);

    const submitBtn = document.getElementById("btn-submit-replace");
    submitBtn.disabled = true;
    submitBtn.textContent = "Uploading & Hashing...";

    try {
        // Uses POST (with PUT alias supported by backend)
        const res = await fetch(`/api/documents/${docId}/content`, {
            method: "POST",
            credentials: "same-origin",
            headers: { "X-XSRF-TOKEN": csrf || "" },
            body: formData
        });

        if (!res.ok) {
            const txt = await res.text();
            throw new Error(txt || `Replacement failed (HTTP ${res.status}).`);
        }

        closeReplaceModal();
        showToast("File replaced and SHA-256 fingerprint generated.", "success");
        await loadDocuments(documentSearch.value);
    } catch (err) {
        replaceModalError.textContent = err.message;
        replaceModalError.style.display = "block";
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = "Upload & Replace";
    }
});

// Upload a document
uploadForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    uploadMessage.textContent = "";

    const submitBtn = document.getElementById("btn-submit-upload");
    const formData = new FormData(uploadForm);
    const csrf = getCsrfToken();

    submitBtn.disabled = true;
    submitBtn.innerHTML = `<span>Encrypting & Storing...</span>`;

    try {
        const response = await fetch("/api/documents", {
            method: "POST",
            credentials: "same-origin",
            headers: { "X-XSRF-TOKEN": csrf || "" },
            body: formData
        });

        if (response.status === 401 || response.redirected) {
            window.location.href = "/login.html";
            return;
        }

        const data = await response.json().catch(() => null);

        if (!response.ok) {
            const errorMsg = (data && (data.message || data.error)) || `Upload rejected (HTTP ${response.status}).`;
            throw new Error(errorMsg);
        }

        uploadForm.reset();
        if (dropzoneFileName) dropzoneFileName.classList.remove("visible");
        showToast(`Document "${data.title || 'File'}" stored securely.`, "success");
        await loadDocuments(documentSearch.value);
    } catch (error) {
        uploadMessage.textContent = error.message;
        uploadMessage.className = "message message-error";
        showToast(error.message, "error");
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = `
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                <polyline points="17 8 12 3 7 8"></polyline>
                <line x1="12" y1="3" x2="12" y2="15"></line>
            </svg>
            Store in Vault
        `;
    }
});

// Load & Filter Documents
async function loadDocuments(searchTerm = "") {
    renderSkeletons();
    listMessage.textContent = "";

    try {
        let url = "/api/documents";
        const hasFilters = searchTerm.trim() ||
            document.getElementById("search-owner").value.trim() ||
            document.getElementById("search-category").value.trim() ||
            document.getElementById("search-from-date").value ||
            document.getElementById("search-to-date").value;

        if (hasFilters) {
            const params = new URLSearchParams();
            if (searchTerm.trim()) params.set("keyword", searchTerm.trim());
            const owner = document.getElementById("search-owner").value.trim();
            if (owner) params.set("owner", owner);
            const cat = document.getElementById("search-category").value.trim();
            if (cat) params.set("category", cat);
            const fromDate = document.getElementById("search-from-date").value;
            if (fromDate) params.set("fromDate", fromDate);
            const toDate = document.getElementById("search-to-date").value;
            if (toDate) params.set("toDate", toDate);

            url = `/api/documents/search?${params.toString()}`;
        }

        const response = await fetch(url, {
            credentials: "same-origin",
            headers: { "Accept": "application/json" }
        });

        if (response.status === 401 || response.redirected) {
            window.location.href = "/login.html";
            return;
        }

        if (!response.ok) {
            throw new Error(`Failed to load documents (HTTP ${response.status}).`);
        }

        const documents = await response.json();
        documentList.innerHTML = "";

        if (!Array.isArray(documents) || documents.length === 0) {
            documentList.innerHTML = `
                <div class="empty-state">
                    <div class="empty-state-icon">
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                            <polyline points="14 2 14 8 20 8"></polyline>
                        </svg>
                    </div>
                    <h3>No documents found</h3>
                    <p>There are no confidential documents matching your current filter criteria.</p>
                </div>
            `;
            return;
        }

        documents.forEach((doc) => {
            documentList.appendChild(createDocumentCard(doc));
        });
    } catch (error) {
        documentList.innerHTML = "";
        listMessage.textContent = error.message;
        listMessage.className = "message message-error";
        showToast(error.message, "error");
    }
}

// Search Inputs & Filters
let debounceTimer;
documentSearch.addEventListener("input", () => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
        loadDocuments(documentSearch.value);
    }, 350);
});

document.getElementById("apply-search-filters").addEventListener("click", () => {
    loadDocuments(documentSearch.value);
});

document.getElementById("clear-search-filters").addEventListener("click", () => {
    document.getElementById("search-owner").value = "";
    document.getElementById("search-category").value = "";
    document.getElementById("search-from-date").value = "";
    document.getElementById("search-to-date").value = "";
    documentSearch.value = "";
    loadDocuments("");
});

document.getElementById("btn-refresh-docs").addEventListener("click", () => {
    loadDocuments(documentSearch.value);
});

// Logout Handler
navLogoutBtn.addEventListener("click", async () => {
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
    const user = await checkSession();
    if (user) {
        await loadDocuments();
    }
})();
