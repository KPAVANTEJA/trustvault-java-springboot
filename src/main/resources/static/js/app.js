
"use strict";

// Smoothly navigate to sections of the Docurithm homepage.
document.querySelectorAll('a[href^="#"]').forEach((link) => {
    link.addEventListener("click", (event) => {
        const targetId = link.getAttribute("href");
        const target = document.querySelector(targetId);

        if (target) {
            event.preventDefault();
            target.scrollIntoView({
                behavior: "smooth",
                block: "start"
            });
        }
    });
});

// Update navigation based on the current login session.
async function updateLoginNavigation() {
    const loginLink = document.querySelector(".login-link");
    const accessLink = document.querySelector(".primary-button");

    if (!loginLink) {
        return;
    }

    try {
        const response = await fetch("/api/auth/me", {
            method: "GET",
            credentials: "same-origin",
            headers: {
                "Accept": "application/json"
            }
        });

        if (!response.ok) {
            showSignedOutState(loginLink, accessLink);
            return;
        }

        const user = await response.json();

        // Confirm the response contains the expected user information.
        if (!user.email || !Array.isArray(user.roles)) {
            showSignedOutState(loginLink, accessLink);
            return;
        }

        // Display the signed-in state.
        loginLink.textContent = "Sign Out";
        loginLink.href = "/logout";

        loginLink.setAttribute("aria-label", "Sign out of Docurithm");

        if (accessLink) {
            accessLink.textContent = "Open Docurithm";
            accessLink.href = "/dashboard.html";
        }
    } catch (error) {
        // Do not assume the user is logged in if the request fails.
        showSignedOutState(loginLink, accessLink);
        console.error("Could not check the login session:", error);
    }
}

function showSignedOutState(loginLink, accessLink) {
    loginLink.textContent = "Sign In";
    loginLink.href = "/login";

    if (accessLink) {
        accessLink.textContent = "Access Docurithm";
        accessLink.href = "/login";
    }
}

// Run when the homepage loads.
updateLoginNavigation();
