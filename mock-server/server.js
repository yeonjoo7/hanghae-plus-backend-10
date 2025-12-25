const http = require('http');

const PORT = 4000;

const server = http.createServer((req, res) => {
    let body = '';

    req.on('data', chunk => {
        body += chunk.toString();
    });

    req.on('end', () => {
        console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);

        // 모든 요청에 대해 성공 응답 반환
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            success: true,
            message: 'Mock server received request',
            timestamp: new Date().toISOString()
        }));
    });
});

server.listen(PORT, () => {
    console.log(`Mock Data Platform Server running on port ${PORT}`);
});
