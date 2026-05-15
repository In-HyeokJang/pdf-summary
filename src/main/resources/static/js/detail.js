function switchTab(id, btn) {
    document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
    document.querySelectorAll('.tab').forEach(el => el.classList.remove('active'));
    document.getElementById(id).classList.add('active');
    btn.classList.add('active');
}

// PROCESSING 상태면 5초마다 자동 새로고침
if (document.body.dataset.status === 'PROCESSING') {
    setTimeout(() => location.reload(), 5000);
}