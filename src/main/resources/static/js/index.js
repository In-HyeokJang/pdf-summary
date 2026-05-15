document.getElementById('uploadForm').addEventListener('submit', function () {
    document.getElementById('uploadingMsg').classList.add('active');
});

// body[data-has-processing] 로 Thymeleaf 서버 값 전달
if (document.body.dataset.hasProcessing === 'true') {
    setTimeout(() => location.reload(), 5000);
}