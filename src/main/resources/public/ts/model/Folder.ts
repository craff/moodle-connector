import http from "axios";
import {Mix} from 'entcore-toolkit';
import {notify} from "entcore";

export interface Folder {
    id: number;
    parent_id: number;
    user_id: string;
    name: string;
    subfolder: Folder[];
    printSubfolder: boolean;
    printTargetSubfolder: boolean;
    select: boolean;
    selectConfirm: boolean;
}

export class Folder {
    constructor() {
        this.printSubfolder = false;
        this.select = false;
        this.parent_id = 0;
        this.selectConfirm = false;
        this.printTargetSubfolder = false;
    }

    toJson() {
        return {
            parentId : this.parent_id,
            userId : this.user_id,
            name : this.name,
        }
    }

    async create() {
        try {
            let {data, status} = await http.post('/moodle/folder', this.toJson());
            return status
        } catch (e) {
            notify.error("Save function didn't work");
            throw e;
        }
    }

    toJsonRename() {
        return {
            id : this.id,
            name : this.name
        }
    }

    async rename() {
        try {
            await http.put('/moodle/folder/rename', this.toJsonRename());
        } catch (e) {
            throw e;
        }
    }
}

export class Folders {
    folderIdMoveIn: number;
    myCourses: Folder;
    all: Folder[];
    search: Folder[];
    isSynchronized: Boolean;
    selectedFolders: number[];
    listSelectedFolders: Folder[];
    listOfSubfolder: Folder[];
    searchInFolders: Folder[];

    constructor() {
        this.all = [];
        this.selectedFolders = [];
        this.listSelectedFolders = [];
        this.listOfSubfolder = [];
        this.folderIdMoveIn = undefined;
        this.isSynchronized = false;
        this.myCourses = new Folder();
        this.myCourses.id = 0;
        this.myCourses.name = "Mes cours";
    }

    toJsonForMove(targetId : number) {
        if(targetId == undefined)
            return {
                parentId: this.folderIdMoveIn,
                foldersId: this.all.filter(folder => folder.select).map(folder => folder.id )
            };
        else
            return {
                parentId: targetId,
                foldersId: this.all.filter(folder => folder.select).map(folder => folder.id ),
            }
    }

    toJsonForDelete() {
        return {
            foldersId: this.all.filter(folder => folder.selectConfirm).map(folder => folder.id ),
        }
    }

    async foldersDelete() {
        try {
            await http.delete('/moodle/folder', { data: this.toJsonForDelete() } );
        } catch (e) {
            notify.error("Delete function didn't work");
            throw e;
        }
    }

    async sync () {
        try {
            let folders = await http.get(`/moodle/folders`);
            this.all = Mix.castArrayAs(Folder, folders.data);
            this.isSynchronized = true;
        } catch (e) {
            this.isSynchronized = false;
            throw e;
        }
    }

    getSubfolder(folderId: number): Folder[] {
        if (this.all) {
            return this.all.filter(folder => folder.parent_id == folderId && folder.id != folderId);
        }
        return [];
    }

    getParentFolder(): Folder[] {
        if (this.all) {
            return this.all.filter(folder => folder.parent_id === 0);
        }
        return [];
    }

    getFoldersToShow(currentFolder, searching): Folder[] {
        currentFolder = this.listOfSubfolder.filter(folder => folder.id == currentFolder);
        if (currentFolder && currentFolder[0]) {
            if (searching !== null && searching !== undefined && searching !== '') {
                if (currentFolder[0].id == 0) {
                    return this.all.filter(folder => folder.name.toLowerCase().includes(searching.toLowerCase()));
                } else {
                    let subfolderSearch = [];
                    for (let i = 0; i < currentFolder[0].subfolder.length; i++) {
                        subfolderSearch.push(currentFolder[0].subfolder[i]);
                        if (currentFolder[0].subfolder[i].subfolder.length != 0) {
                            subfolderSearch.push(...this.getFoldersToShow(currentFolder[0].subfolder[i].id, searching));
                        }
                    }
                    return subfolderSearch.filter(folder => folder.name.toLowerCase().includes(searching.toLowerCase()));
                }
            } else if (currentFolder[0].id == 0) {
                return this.getParentFolder();
            } else {
                return this.getSubfolder(currentFolder[0].id);
            }
        }
    }


    getAllFoldersModel(): Folder[] {
        if (this.all) {
            return this.all;
        }
        return [];
    }

    getAllSubfolder() {
        if (this.all) {
            this.listOfSubfolder.length = 0;
            this.listOfSubfolder.push(this.myCourses);
            this.getParentFolder().forEach(folder => {
                if (!folder.select) {
                    this.listOfSubfolder.push(folder);
                    folder.subfolder = this.getSubfolder(folder.id);
                    this.insertSubfolder(folder);
                }
            });
        }
    }

    insertSubfolder(folder: Folder) {
        if (folder.subfolder && folder.subfolder.length) {
            this.getSubfolder(folder.id).forEach(subfolder => {
                if (!(subfolder.select)) {
                    this.listOfSubfolder.push(subfolder);
                    subfolder.subfolder = this.getSubfolder(subfolder.id);
                    this.insertSubfolder(subfolder);
                }
            });
        }
    }

    async moveToFolder(targetId: number) {
        try {
            await http.put(`/moodle/folders/move`, this.toJsonForMove(targetId));
        } catch (e) {
            throw e;
        }
    }
}
