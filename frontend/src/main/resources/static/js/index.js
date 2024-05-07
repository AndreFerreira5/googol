let isTransitioning = false;
var operationType = "search";
updateDocumentByOperation();


function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}


async function animateHeader() {
    const header = document.getElementById('title');
    const text = header.textContent;
    const letters = text.split('');

    // animate each letter in the text
    const animateLetters = async () => {
        for (let i = 0; i < letters.length; i++) {
            const letter = document.createElement('span');
            letter.textContent = letters[i];
            letter.classList.add('transition-all', 'duration-700', 'ease-in-out', 'text-black');

            header.appendChild(letter);

            setTimeout(() => {
                if (Math.random() < 0.2) {
                    letter.classList.remove('text-black');
                    letter.classList.add('text-blue-500');
                }
            }, 150);
        }
    };

    // reset header content and restart the animation cycle
    const resetHeader = async () => {
        // clear any existing child elements while keeping the original text intact
        header.innerHTML = '';
        await animateLetters();

        // pause before resetting to create an indefinite looping effect
        await sleep(5000);
        resetHeader();
    };

    // start the first animation cycle
    resetHeader();
}

window.addEventListener('DOMContentLoaded', animateHeader);


async function updateDocumentByOperation(){
    const operationButton = document.getElementById("operationButton");
    operationButton.classList.remove('bg-blue-500');
    operationButton.classList.add('bg-blue-700');
    operationButton.classList.add('text-transition');
    await sleep(75);
    switch(operationType){
        case "search":
            operationButton.innerText = "Search";
            break;
        case "index":
            operationButton.innerText = "Index";
            break;
        case "fathers":
            operationButton.innerText = "Fathers";
            break;
    }
    await sleep(75);
    operationButton.classList.remove('text-transition');
    operationButton.classList.remove('bg-blue-700');
    operationButton.classList.add('bg-blue-500');
}


async function showOperationButton(button) {
    button.classList.remove('invisible', '-translate-x-10');
    button.classList.add('translate-x-2/4');
    await sleep(300);
    isTransitioning = false;
}

async function hideOperationButton(button) {
    await sleep(300);
    if (!button.matches(':hover')) {
        isTransitioning = true;
        button.classList.remove('translate-x-2/4');
        button.classList.add('-translate-x-10');
        await sleep(300);
        button.classList.add('invisible');
    }
}


async function showOperationsDiv(changeOperationButton, searchOperationButton, indexOperationButton, fathersOperationButton) {
    if(isTransitioning) return;

    changeOperationButton.classList.remove('bg-blue-500');
    changeOperationButton.classList.add('bg-slate-200');
    changeOperationButton.classList.remove('w-0', 'h-0');
    changeOperationButton.classList.add('w-28', 'h-40');
    await sleep(300);
    searchOperationButton.classList.remove('hidden');
    searchOperationButton.classList.remove('opacity-0');
    searchOperationButton.classList.add('opacity-100');
    indexOperationButton.classList.remove('hidden');
    indexOperationButton.classList.remove('opacity-0');
    indexOperationButton.classList.add('opacity-100');
    fathersOperationButton.classList.remove('hidden');
    fathersOperationButton.classList.remove('opacity-0');
    fathersOperationButton.classList.add('opacity-100');
}


async function hideOperationsDiv(changeOperationButton, searchOperationButton, indexOperationButton, fathersOperationButton) {
    changeOperationButton.classList.remove('w-28', 'h-40');
    changeOperationButton.classList.add('w-0', 'h-0');
    searchOperationButton.classList.remove('opacity-100');
    searchOperationButton.classList.add('opacity-0');
    searchOperationButton.classList.add('hidden');
    indexOperationButton.classList.remove('opacity-100');
    indexOperationButton.classList.add('opacity-0');
    indexOperationButton.classList.add('hidden');
    fathersOperationButton.classList.remove('opacity-100');
    fathersOperationButton.classList.add('opacity-0');
    fathersOperationButton.classList.add('hidden');
    changeOperationButton.classList.remove('bg-slate-200');
    changeOperationButton.classList.add('bg-blue-500');
}


document.addEventListener("DOMContentLoaded", function() {
    const operationButton = document.getElementById('operationButton');
    const changeOperationButton = document.getElementById('changeOperationButton');
    const searchOperationButton = document.getElementById('searchOperationButton');
    const indexOperationButton = document.getElementById('indexOperationButton');
    const fathersOperationButton = document.getElementById('fathersOperationButton');
    const content = document.getElementById('content');

    //operationButton.addEventListener('click', () => {
        //content.classList.remove('opacity-100');
        //content.classList.add('opacity-0');
    //});
    operationButton.addEventListener('mouseenter', () => showOperationButton(changeOperationButton));
    operationButton.addEventListener('mouseleave', () => {
        if (!changeOperationButton.matches(':hover')) {
            hideOperationButton(changeOperationButton);
        }
    });
    operationButton.addEventListener('mouseleave', () => hideOperationButton(changeOperationButton));

    
    changeOperationButton.addEventListener('mouseenter', () => showOperationsDiv(changeOperationButton, searchOperationButton, indexOperationButton, fathersOperationButton));
    changeOperationButton.addEventListener('mouseleave', () => {
        hideOperationsDiv(changeOperationButton, searchOperationButton, indexOperationButton, fathersOperationButton)
        hideOperationButton(changeOperationButton);
    });
    changeOperationButton.removeEventListener('mouseenter', () => {});
    changeOperationButton.removeEventListener('mouseleave', () => {});

    searchOperationButton.addEventListener("click", function(){
        event.preventDefault();

        switch(operationType){
            case "search":
                return;
            case "index":
                indexOperationButton.classList.remove('bg-blue-500');
                indexOperationButton.classList.add('bg-slate-300');
                break;
            case "fathers":
                fathersOperationButton.classList.remove('bg-blue-500');
                fathersOperationButton.classList.add('bg-slate-300');
                break;
        }

        operationType = "search";
        updateDocumentByOperation();
        searchOperationButton.classList.remove('bg-slate-300');
        searchOperationButton.classList.add('bg-blue-500');
    });

    indexOperationButton.addEventListener("click", function(){
        event.preventDefault();

        switch(operationType){
            case "search":
                searchOperationButton.classList.remove('bg-blue-500');
                searchOperationButton.classList.add('bg-slate-300');
                break;
            case "index":
                return;
            case "fathers":
                fathersOperationButton.classList.remove('bg-blue-500');
                fathersOperationButton.classList.add('bg-slate-300');
                break;
        }

        operationType = "index";
        updateDocumentByOperation();
        indexOperationButton.classList.remove('bg-slate-300');
        indexOperationButton.classList.add('bg-blue-500');
    });

    fathersOperationButton.addEventListener("click", function(){
        event.preventDefault();

        switch(operationType){
            case "index":
                indexOperationButton.classList.remove('bg-blue-500');
                indexOperationButton.classList.add('bg-slate-300');
                break;
            case "search":
                searchOperationButton.classList.remove('bg-blue-500');
                searchOperationButton.classList.add('bg-slate-300');
                break;
            case "fathers":
                return;
        }

        operationType = "fathers";
        updateDocumentByOperation();
        fathersOperationButton.classList.remove('bg-slate-300');
        fathersOperationButton.classList.add('bg-blue-500');
    });
});
