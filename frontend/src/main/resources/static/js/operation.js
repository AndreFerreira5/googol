let page = 0;
let pageSize = 10;
let isFreshSearch = true;
let currentPageElement = document.getElementById('currentPage');
let lastPageElement = document.getElementById('lastPage');
let lastPage = 0;

// TODO implement a more sophisticated page navigation system where a button disappears if its not possible to go further or backwards


function displaySearchResults(data){
    // clear existing results
    $('#searchResults').empty();

    data.forEach(function(urlInfo){
        if(urlInfo == null || urlInfo[0] == null) return;

        // create the main container div
        const containerDiv = document.createElement('div');
        containerDiv.className = 'flex flex-col w-11/12 h-auto rounded-2xl bg-slate-200 p-4';

        // create the first paragraph element
        const paragraph1 = document.createElement('a');
        paragraph1.className = 'text-[11px]';
        paragraph1.textContent = urlInfo[0];
        paragraph1.href = urlInfo[0];

        // create the second paragraph element
        const paragraph2 = document.createElement('a');
        paragraph2.className = 'text-lg';
        paragraph2.textContent = urlInfo[1];
        paragraph2.href = urlInfo[0];

        // create the third paragraph element
        const paragraph3 = document.createElement('p');
        paragraph3.className = 'text-base';
        paragraph3.textContent = urlInfo[2];

        /*
        const aiContextButton = document.createElement('div');
        aiContextButton.classList.add('flex', 'flex-row', 'items-center', 'justify-center', 'rounded-full', 'bg-green-200/50', 'w-12');
        const text = document.createElement('p');
        text.className = 'text-sm';
        text.textContent = "+";
        aiContextButton.append(text);
         */

        // append the paragraphs to the main container div
        containerDiv.appendChild(paragraph1);
        containerDiv.appendChild(paragraph2);
        containerDiv.appendChild(paragraph3);
        //containerDiv.appendChild(aiContextButton);

        $('#searchResults').append(containerDiv);
    });
}


async function getSearchAnalysis(serializedQuery){
    const analysisContainer = document.getElementById('analysisContainer');
    analysisContainer.innerHTML = '';
    analysisContainer.classList.remove('hidden');

    const fetchingText = document.createElement('p');
    analysisContainer.append(fetchingText);
    fetchingText.textContent = "Fetching analysis...";

    console.log(serializedQuery);
    const ajaxOptions = {
        type: 'GET',
        url: '/api/analysis/google',
        data: serializedQuery,
        success: function(result) {
            console.log(result);
            fetchingText.textContent = result;
        },
        error: function() {
            console.log("Error fetching analysis");
            fetchingText.textContent = "Couldn't fetch analysis";

            sleep(2000);
            analysisContainer.classList.add('opacity-0');
        }
    };

    $.ajax(ajaxOptions);
}


function performSearch() {
    let formData = $('#operationForm').serialize() + '&page=' + page + '&pageSize=' + pageSize + '&isFreshSearch=' + isFreshSearch;
    $.ajax({
        type: 'GET',
        url: '/api/search',
        data: formData,
        success: function(data) {
            // when no results are found
            if (data == null || data.length === 0) {
                $('#searchResults').empty();
                $('#operationResults').html("No results found!");
                return;
            }

            // get the last page that comes in the response from the backend
            lastPage = parseInt(data.pop()[0], 10);
            lastPageElement.innerHTML = (lastPage + 1).toString();

            displaySearchResults(data);

            if (isFreshSearch) {
                page = 0; // reset to first page if it's a fresh search
                getSearchAnalysis($('#operationForm').serialize());
                fetchHackerNews($('#operationForm').serialize());
            }
            isFreshSearch = false; // reset flag after initial load
        },
        error: function() {
            $('#operationResults').html('<p>An error has occurred</p>');
        }
    });
}


function performIndexation(){
    let formData = $('#operationForm');
    let operationResults = $('#operationResults');
    operationResults.empty();

    const urlInputs = document.querySelectorAll('.url-input');
    const urls = [];

    urlInputs.forEach(input => {
        if (input.value) {
            urls.push(input.value);
        }
    });

    const singleUrl = urls.length === 1;
    const urlParams = new URLSearchParams();
    urls.forEach(url => urlParams.append('urls', url));

    const ajaxOptions = {
        type: 'POST',
        data: urlParams.toString(),
        contentType: 'application/x-www-form-urlencoded; charset=UTF-8',
        success: function(result) {
            let message;
            if(singleUrl){
                message = result ? 'Indexation successful' : 'Indexation failed';
            } else {
                message = (result === null || result === "") ? 'Indexation successful' : 'Failed to index the following URLs: ' + result;
            }
            $('.url-input').not(':first').parent().remove();
            $('.url-input').val('');

            let messageElement = $('<p></p>').text(message).addClass('transition-all duration-500 ease-in-out opacity-100');
            operationResults.append(messageElement);

            setTimeout(() => {
                messageElement.addClass('opacity-0');
                setTimeout(() => {
                    messageElement.remove();
                }, 500);
            }, 5000);
        },
        error: function() {
            let errorMessage = $('<p></p>').text('An error has occurred').addClass('transition-all duration-500 ease-in-out opacity-100');
            operationResults.append(errorMessage);

            setTimeout(() => {
                errorMessage.addClass('opacity-0');
                setTimeout(() => {
                    errorMessage.remove();
                }, 500);
            }, 5000);
        }
    };

    if (singleUrl) {
        ajaxOptions.url = '/api/index';
        ajaxOptions.data = `url=${urls[0]}`;
    } else {
        ajaxOptions.url = '/api/index/multiple';
    }

    $.ajax(ajaxOptions);
}


function getUrlFathers() {
    const urlInputs = document.querySelectorAll('.url-input');
    const urls = [];

    urlInputs.forEach(input => {
        if (input.value) {
            urls.push(input.value);
        }
    });

    let operationResults = $('#operationResults');
    operationResults.empty();

    $.ajax({
        type: 'GET',
        url: '/api/fathers',
        data: 'url=' + urls[0],
        success: function(result) {
            // when no results are found
            if (result == null || result.length === 0) {
                $('#operationResults').html("No fathers found!");
                return;
            }

            result.forEach(function(urlInfo){
                if(urlInfo == null) return;

                let urlDiv = $('<div class="search-result-item flex flex-col w-11/12 h-auto rounded-2xl bg-slate-200 p-4"></div>');
                urlDiv.append($('<a></a>').attr('href', urlInfo).text(urlInfo));

                $('#operationResults').append(urlDiv);
            });
        },
        error: function() {
            $('#operationResults').html('<p>An error has occurred</p>');
        }
    });
}


$('#nextPage').click(function() {
    if(page === lastPage) return;
    ++page;
    currentPageElement.innerHTML = (page + 1).toString();
    performSearch();
});

$('#previousPage').click(function() {
    if(page <= 0) return;
    --page;
    currentPageElement.innerHTML = (page + 1).toString();
    performSearch();
});

$('#operationForm').submit(function(event) {
    event.preventDefault();

    let query;
    switch (window.operationType){
        case "search":
            document.getElementById('operationResults').innerHTML = '';
            document.getElementById('searchContainer').classList.remove('hidden');
            query = document.getElementById('operationQuery').value;
            if(query === "") return;
            isFreshSearch = true;
            performSearch();
            document.getElementById('pageNavigators').classList.remove('hidden');
            //fetchHackerNews(query);
            currentPageElement.innerHTML = (page + 1).toString();
            break;

        case "index":
            document.getElementById('operationResults').innerHTML = '';
            document.getElementById('searchContainer').classList.add('hidden');
            query = document.getElementById('operationQuery').value;
            if(query === "") return;
            performIndexation();
            break;

        case "fathers":
            document.getElementById('operationResults').innerHTML = '';
            document.getElementById('searchContainer').classList.add('hidden');
            query = document.getElementById('operationQuery').value;
            if(query === "") return;
            getUrlFathers();
            break;
    }
});


async function indexHackerNews(data){
    const urls = data.map(item => item.url);

    const urlParams = new URLSearchParams();
    urls.forEach(url => urlParams.append('urls', url));

    $.ajax({
        type: 'POST',
        url: '/api/index/multiple',
        data: urlParams.toString()
    });
}


async function fetchHackerNews(query) {
    let url = `/api/hacker-news?` + query;

    // TODO make a fancy loading animation instead of just text
    let hackerNewsStoriesDiv = document.getElementById('hackerNewsStories');
    hackerNewsStoriesDiv.innerHTML = '';

    const hackerNewsContent = document.createElement('div');
    hackerNewsContent.classList.add('bg-green-200/75', 'rounded-2xl', 'p-4', 'w-3/4');

    // Create a paragraph element with text content
    const loadingText = document.createElement('p');
    loadingText.textContent = 'Fetching Hacker News stories...';
    hackerNewsContent.appendChild(loadingText);

    hackerNewsStoriesDiv.appendChild(hackerNewsContent);

    try {
        const response = await fetch(url);
        const data = await response.json();

        // clear any existing results
        hackerNewsContent.innerHTML = '';

        // check if data is available
        if (data == null || data.length === 0) {
            const noDataText = document.createElement('p');
            noDataText.textContent = 'No matching Hacker News stories found.';
            hackerNewsContent.appendChild(noDataText);
            return;
        }

        const foundText = document.createElement('p');
        foundText.textContent = 'Found ' + data.length + ' Hacker News stories matching your search.';
        hackerNewsContent.appendChild(foundText);

        const indexButton = document.createElement('button');
        indexButton.classList.add('bg-green-200', 'rounded-full', 'px-2', 'py-2', 'transition-all', 'duration-300', 'ease-in-out');
        indexButton.textContent = 'Index pages';
        hackerNewsContent.appendChild(indexButton);

        indexButton.addEventListener("click", function() {
            indexHackerNews(data);
            indexButton.textContent = 'Pages Indexed!';
            indexButton.disabled = true;
        });

    } catch (error) {
        console.error('Error fetching Hacker News data: ', error);
    }
}

