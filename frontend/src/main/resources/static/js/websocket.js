const socket = new WebSocket('ws://localhost:8080/ws');

socket.onopen = () => {
    console.log("WebSocket connection established.");
};

socket.onmessage = (event) => {
    const message = event.data;
    console.log("Received WebSocket message: " + message);
    // Handle the message data here
};

socket.onclose = () => {
    console.log("WebSocket connection closed.");
};
