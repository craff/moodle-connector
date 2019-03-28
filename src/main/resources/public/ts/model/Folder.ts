import http from "axios";
import {Mix} from 'entcore-toolkit';
import {notify} from "entcore";
import {Author} from "./Course";

export interface Folder {
    id : number;
    parent_id : number;
    user_id : string;
    name : string;
    structure_id : string;
    subFolders : Folder[];
    printsubfolder : boolean;
    printTargetsubfolder: boolean;
    select: boolean;
    selectConfirm: boolean;
}

export class Folder {
    constructor() {
        this.printsubfolder = false;
        this.select = false;
        this.parent_id = 0;
        this.selectConfirm=false;
        this.printTargetsubfolder=false;
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

}

export class Folders {
    folderIdMoveIn: number;
    all: Folder[];
    isSynchronized: Boolean;
    selectedFolders: number[];
    listSelectedFolders: Folder[];

    constructor() {
        this.all = [];
        this.selectedFolders = [];
        this.listSelectedFolders = [];
        this.isSynchronized = false;
    }

    toJsonForMove(){
        return {
            parentId: this.folderIdMoveIn,
            foldersId: this.all.filter(folder => folder.select).map(folder => folder.id ),
        }
    }

    toJsonForDelete(){
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
    getSubFolder(folderId:number): Folder[]  {
        if(this.all){
            return this.all.filter(folder=>folder.parent_id == folderId &&  folder.id != folderId);
        }
        return [];
    }
    getparentFolder(): Folder[]  {
        if(this.all){
            return this.all.filter(folder=>folder.parent_id === 0);
        }
        return [];
    }
    getAllFoldersModel(): Folder[]  {
        if(this.all){
            return this.all;
        }
        return [];
    }

    async moveFolders () {
        try {
            await http.put(`/moodle/folders/move`, this.toJsonForMove());
        } catch (e) {
            throw e;
        }
    }
}