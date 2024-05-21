/* DEFAULT VALUES */
let host = "localhost";
let port = 8080;
let websocketEndpoint = "ws";

async function processWebsocketMessage(data){
    try{
        if(data === null || data === ""){
            //TODO write a message on the html saying the system info couldnt be received
        }


        console.log(data);
        // parse JSON data into an object
        const parsedData = JSON.parse(data);

        // extract individual properties from the JSON object
        const barrelsInfo = parsedData.barrelsInfo;
        const downloadersInfo = parsedData.downloadersInfo;
        const urlsToProcess = parsedData.urlsToProcess;
        const topSearches = parsedData.topSearches;

        console.log('Barrels Info:', barrelsInfo);
        console.log('Downloaders Info:', downloadersInfo);
        console.log('URLs to Process:', urlsToProcess);
        console.log('Top Searches:', topSearches);
        //let barrelsInfo = data[0]; // [[name1|responseTime1|availability1|requestCount1], [name2|responseTime2|availability2|requestCount2], ...]
        //let downloadersInfo = data[1]; // [[name1], [name2], ...]
        //let urlsToProcess = data[2][0]; // number
        //let topSearches = data[3]; // [[search1|count1], [search2|count2], [], ...]

        let barrelsInfoDiv = document.getElementById('barrelsInfo')
        barrelsInfoDiv.innerHTML = ""; // clear existing content

        const barrelTitle = document.createElement('p');
        barrelTitle.textContent = 'BARRELS';
        barrelTitle.classList.add('text-lg', 'text-bold');
        barrelsInfoDiv.append(barrelTitle);

        barrelsInfo.forEach((unprocesedBarrelInfo) => {
            // create a new container for each barrel
            let barrelInfoDiv = document.createElement('div');
            barrelInfoDiv.classList.add('p-2', 'bg-blue-500/75', 'hover:bg-blue-600/75', 'transition-all', 'duration-150', 'rounded-2xl');

            // split the barrel information string and assign each value to an element
            let barrelInfo = unprocesedBarrelInfo.split('|');
            let barrelNameSplit = barrelInfo[0].split('/');
            let barrelName = document.createElement('p');
            barrelName.textContent = `${barrelNameSplit[3]}`;
            let barrelHost = document.createElement('p');
            barrelHost.textContent = `Host: ${barrelNameSplit[2]}`;
            let barrelResponseTime = document.createElement('p');
            barrelResponseTime.textContent = `Average Response Time: ${barrelInfo[1]}`;
            let barrelAvailability = document.createElement('p');
            barrelAvailability.textContent = `Load: ${barrelInfo[2]}`;
            let barrelRequestCount = document.createElement('p');
            barrelRequestCount.textContent = `Request Count: ${barrelInfo[3]}`;

            // append details to the main barrel info div
            barrelInfoDiv.append(barrelName);
            barrelInfoDiv.append(barrelHost);
            barrelInfoDiv.append(barrelResponseTime);
            barrelInfoDiv.append(barrelAvailability);
            barrelInfoDiv.append(barrelRequestCount);

            /*
            // add donut div that represents each barrel load
            let loadDonut = document.createElement('div');
            loadDonut.classList.add('donut', 'border-4', 'border-gray-400', 'bg-transparent');
            barrelInfoDiv.append(loadDonut);
            */

            // append the barrel info div to the main div
            barrelsInfoDiv.append(barrelInfoDiv);
        });


        /* DOWNLOADERS INFO */
        let downloadersInfoDiv = document.getElementById('downloadersInfo');
        downloadersInfoDiv.innerHTML = ""; // clear existing content

        const downloaderTitle = document.createElement('p');
        downloaderTitle.textContent = 'DOWNLOADERS';
        downloaderTitle.classList.add('text-lg', 'text-bold');
        downloadersInfoDiv.append(downloaderTitle);

        downloadersInfo.forEach((downloaderID) => {
            // create a new container for each downloader
            let downloaderInfoDiv = document.createElement('div');
            downloaderInfoDiv.classList.add('p-2', 'bg-blue-500/75', 'hover:bg-blue-600/75', 'transition-all', 'duration-150', 'rounded-2xl');

            let downloaderName = document.createElement('p');
            downloaderName.textContent = `${downloaderID}`;

            // append download details to the main downloader info div
            downloaderInfoDiv.append(downloaderName);

            // append the barrel info div to the main div
            downloadersInfoDiv.append(downloaderInfoDiv);
        });


        /* URLS TO PROCESS INFO */
        let urlsToProcessDiv = document.getElementById('urlsToProcess');
        urlsToProcessDiv.innerHTML = ""; // clear existing content

        const urlsToProcessTitle = document.createElement('p');
        urlsToProcessTitle.textContent = 'URLS TO PROCESS';
        urlsToProcessTitle.classList.add('text-lg', 'text-bold');
        urlsToProcessDiv.append(urlsToProcessTitle);

        let urlsToProcessInfoDiv = document.createElement('div');
        urlsToProcessInfoDiv.classList.add('p-2', 'bg-transparent', 'transition-all', 'duration-150', 'flex', 'flex-col', 'items-center', 'justify-center');

        let urlsToProcessText = document.createElement('p');
        urlsToProcessText.textContent = `${urlsToProcess}`;
        urlsToProcessInfoDiv.append(urlsToProcessText);

        urlsToProcessDiv.append(urlsToProcessInfoDiv);


        /* TOP TEN SEARCHES */
        let topTenSearchesDiv = document.getElementById('topTenSearches');
        topTenSearchesDiv.innerHTML = ""; // clear existing content

        const topTenSearchesTitle = document.createElement('p');
        topTenSearchesTitle.textContent = 'TOP TEN SEARCHES';
        topTenSearchesTitle.classList.add('text-lg', 'text-bold');
        topTenSearchesDiv.append(topTenSearchesTitle);

        // create a new container for each downloader
        let topTenSearchesSubDiv = document.createElement('div');
        topTenSearchesSubDiv.classList.add('p-2', 'bg-transparent', 'transition-all', 'duration-150', 'rounded-2xl', 'flex', 'flex-col', 'items-center', 'justify-center', 'space-y-2');

        topSearches.forEach((search) => {
            let searchSplit = search.split("|");
            let searchInfo = document.createElement('p');
            searchInfo.textContent = `${searchSplit[0]}: ${searchSplit[1]}`;

            // append download details to the main downloader info div
            topTenSearchesSubDiv.append(searchInfo);
        });

        topTenSearchesDiv.append(topTenSearchesSubDiv);
    } catch (error) {
        console.error('Error processing WebSocket message:', error);
    }
}

async function fetchHostAndPort() {
    try {
        const hostResponse = await fetch('/api/config/host');
        if (!hostResponse.ok) {
            throw new Error('Network response was not ok (host).');
        }
        host = await hostResponse.text();

        const portResponse = await fetch('/api/config/port');
        if (!portResponse.ok) {
            throw new Error('Network response was not ok (port).');
        }
        port = await portResponse.text();

        const websocketEndpointResponse = await fetch('/api/config/websocket-endpoint');
        if (!websocketEndpointResponse.ok) {
            throw new Error('Network response was not ok (websocket endpoint).');
        }
        websocketEndpoint = await websocketEndpointResponse.text();

        const socket = new WebSocket(`wss://${host}:${port}/${websocketEndpoint}`);

        socket.onopen = () => {
            console.log("WebSocket connection established.");
        };

        socket.onmessage = (event) => {
            const message = event.data;
            processWebsocketMessage(message);
        };

        socket.onclose = () => {
            console.log("WebSocket connection closed.");
        };

        socket.onerror = (error) => {
            console.error('WebSocket error:', error);
        };

    } catch (error) {
        console.error('Error during fetch:', error);
    }
}

fetchHostAndPort();


let adminPanelState = false;
document.getElementById('adminButton').addEventListener("click", function (){
    adminPanelState = !adminPanelState;
    if(adminPanelState){
        document.getElementById('openAdminSVG').classList.add('hidden');
        document.getElementById('closeAdminSVG').classList.remove('hidden');
        document.getElementById('adminContainer').classList.remove('hidden');
    }
    else{
        document.getElementById('adminContainer').classList.add('hidden');
        document.getElementById('closeAdminSVG').classList.add('hidden');
        document.getElementById('openAdminSVG').classList.remove('hidden');
    }



});