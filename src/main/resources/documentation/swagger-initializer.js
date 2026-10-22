window.addEventListener("load", () => {
    SwaggerUIBundle({
        url: "/openapi.yaml",
        dom_id: "#swagger-ui",
        deepLinking: true,
        persistAuthorization: false,
        validatorUrl: null,
    });
});
