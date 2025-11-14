"use strict";
const upload = document.getElementById("upload");
const canvas = document.getElementById("canvas");
const ctx = canvas.getContext("2d");
upload.onchange = () => {
    var _a;
    const file = (_a = upload.files) === null || _a === void 0 ? void 0 : _a[0];
    if (!file)
        return;
    const img = new Image();
    img.src = URL.createObjectURL(file);
    img.onload = () => {
        canvas.width = img.width;
        canvas.height = img.height;
        ctx === null || ctx === void 0 ? void 0 : ctx.drawImage(img, 0, 0);
    };
};
