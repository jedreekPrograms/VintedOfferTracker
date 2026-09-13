import AdditionalProductsPanel
    from "../features/bots/edit/AdditionalProductsPanel";
import DeleteBotPanel
    from "../features/bots/delete/DeleteBotPanel";

import EditBotPage
    from "./EditBotPage";


function EditBotWithDeletePage() {

    return (
        <>
            <EditBotPage />
            <AdditionalProductsPanel />
            <DeleteBotPanel />
        </>
    );
}


export default EditBotWithDeletePage;
