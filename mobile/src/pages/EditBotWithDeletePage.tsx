import DeleteBotPanel
    from "../features/bots/delete/DeleteBotPanel";

import EditBotPage
    from "./EditBotPage";


function EditBotWithDeletePage() {

    return (
        <>
            <EditBotPage />
            <DeleteBotPanel />
        </>
    );
}


export default EditBotWithDeletePage;
