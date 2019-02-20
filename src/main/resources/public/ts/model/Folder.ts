import http from "axios";
import {Mix} from 'entcore-toolkit';
import {notify} from "entcore";

export class Folder {
    id : number;
    parent_id : number;
    user_id : string;
    name : string;
    structure_id : string;
    nbItems: number=0;
    subFolders : Folder[];
    printsubfolder : boolean=false;
    select: boolean=false;
    toJSON() {
        return {
            parentId : this.parent_id,
            userId : this.user_id,
            name : this.name,
            structureId : this.structure_id,
        }
    }
    async create() {
        try {
            await http.post('/moodle/folder', this.toJSON());
        } catch (e) {
            notify.error("Save function didn't work");
            throw e;
        }
    }
    async countItemsModel () {
        try {
            let countsfolders = await http.get(`/moodle/folder/countsFolders/${this.id}`);
            let countscourses = await http.get(`/moodle/folder/countsCourses/${this.id}`);
            if(countsfolders && countscourses){
                this.nbItems=countsfolders.data.count+countscourses.data.count;
            }
        } catch (e) {
            throw e;
        }
    }
}

export class Folders {
    folderIdMoveIn: string;
    all: Folder[];
    isSynchronized: Boolean;
    constructor() {
        this.all = [];
        this.isSynchronized = false;
    }
    toJsonForMove(){
        return {
            parentId: parseInt(this.folderIdMoveIn, 10),
            foldersId: this.all.filter(folder => folder.select).map(folder => folder.id ),
        }
    }
    async moveFolders() {
        try {
            await http.put('/moodle/folder/move', this.toJsonForMove());
        } catch (e) {
            notify.error("Move function didn't work");
            throw e;
        }
    }
    toJsonForDelete(){
        return {
            foldersId: this.all.filter(folder => folder.select).map(folder => folder.id ),
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
}