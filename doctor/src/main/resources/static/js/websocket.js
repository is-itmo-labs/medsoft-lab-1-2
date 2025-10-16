// websocket.js

let stompClient = null;

function connectWebSocket() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function (frame) {
        console.log('WebSocket соединение установлено:', frame);

        stompClient.subscribe('/topic/patients', function (message) {
            console.log('Новое обновление пациентов:', message.body);
            updatePatientTable();
        });
    }, function (error) {
        console.error('Ошибка WebSocket:', error);
        setTimeout(connectWebSocket, 5000); // попытка переподключения
    });
}

document.addEventListener('DOMContentLoaded', function () {
    connectWebSocket();
});
