import http from "axios";
import {Mix} from 'entcore-toolkit';
import {notify} from "entcore";

export interface Folder {
    id : number;
    parent_id : number;
    user_id : string;
    name : string;
    structure_id : string;
    subFolders : Folder[];
    printsubfolder : boolean;
    printTargetsubfolder : boolean;
    select : boolean;
    selectConfirm : boolean;
}

export class Folder {
    constructor() {
        this.printsubfolder = false;
        this.select = false;
        this.parent_id = 0;
        this.selectConfirm = false;
        this.printTargetsubfolder = false;
    }

    toJson() {
        return {
            parentId : this.parent_id,
            userId : this.user_id,
            name : this.name,
            structureId : this.structure_id,
        }
    }

    async create() {
        try {
            await http.post('/moodle/folder', this.toJson());
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
    folderIdMoveIn : number;
    myCourses : Folder;
    all : Folder[];
    search : Folder[];
    isSynchronized : Boolean;
    selectedFolders : number[];
    listSelectedFolders : Folder[];
    listOfSubfolders : Folder[];
    searchInFolders : Folder[];

    constructor() {
        this.all = [];
        this.selectedFolders = [];
        this.listSelectedFolders = [];
        this.listOfSubfolders = [];
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

    getSubFolder(folderId : number): Folder[] {
        if(this.all){
            return this.all.filter(folder => folder.parent_id == folderId && folder.id != folderId);
        }
        return [];
    }

    getparentFolder(): Folder[] {
        if(this.all){
            return this.all.filter(folder => folder.parent_id === 0);
        }
        return [];
    }

    getFoldersToShow(currentFolder, searching): Folder[] {
        currentFolder = this.listOfSubfolders.filter(folder => folder.id == currentFolder);
        if (searching !== null && searching !== undefined && searching !== '') {
            if (currentFolder[0].id == 0 ){
                return this.all.filter(folder => folder.name.toLowerCase().includes(searching.toLowerCase()));
            } else {
                let subFoldersSearch = [];
                for (let i = 0; i < currentFolder[0].subFolders.length; i++) {
                    subFoldersSearch.push(currentFolder[0].subFolders[i]);
                    if (currentFolder[0].subFolders[i].subFolders.length != 0) {
                        subFoldersSearch.push(...this.getFoldersToShow(currentFolder[0].subFolders[i].id, searching));
                    }
                }
                return subFoldersSearch.filter(folder => folder.name.toLowerCase().includes(searching.toLowerCase()));
            }
        } else if (currentFolder[0].id == 0) {
            return this.getparentFolder();
        } else {
            return this.getSubFolder(currentFolder[0].id);
        }
    }

    getAllFoldersModel(): Folder[] {
        if(this.all){
            return this.all;
        }
        return [];
    }

    getAllsubfolders() {
        if (this.all) {
            this.listOfSubfolders.length = 0;
            this.listOfSubfolders.push(this.myCourses);
            this.getparentFolder().forEach(folder => {
                if (!folder.select) {
                    this.listOfSubfolders.push(folder);
                    folder.subFolders = this.getSubFolder(folder.id);
                    this.insertSubFolders(folder);
                }
            });
        }
    }

    insertSubFolders(folder:Folder) {
        if (folder.subFolders && folder.subFolders.length) {
            this.getSubFolder(folder.id).forEach(subFolder => {
                if(!(subFolder.select)) {
                    this.listOfSubfolders.push(subFolder);
                    subFolder.subFolders = this.getSubFolder(subFolder.id);
                    this.insertSubFolders(subFolder);
                }
            });
        }
    }

    async moveToFolder(targetId : number) {
        try {
            await http.put(`/moodle/folders/move`, this.toJsonForMove(targetId));
        } catch (e) {
            throw e;
        }
    }
}
