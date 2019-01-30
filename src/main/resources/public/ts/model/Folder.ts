import http from "axios";
import {Mix, Selectable, Selection} from 'entcore-toolkit';

export class Folder {
    id : number;
    parent_id : number;
    user_id : string;
    name : string;
    structure_id : string;
    nbItems: number=0;
    subFolders : Folder[];
    printfolder: boolean=true;
    printsubfolder: boolean=false;
    toJson() {
        return {
            id : this.id,
            parentid : this.parent_id,
            userid : this.user_id,
            name : this.name,
            structureid : this.structure_id
        }

    }
    async countitems () {
        try {
            let counts = await http.get(`/moodle/folder/counts/${this.id}`);
            if(counts){
                this.nbItems=counts.data.count;
            }

        } catch (e) {
            throw e;
        }
    }
}

export class Folders {
    all: Folder[];
    constructor() {
        this.all = [];
    }
    async sync () {
        try {
            let folders = await http.get(`/moodle/folders`);
            this.all = Mix.castArrayAs(Folder, folders.data);
        } catch (e) {
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
            return this.all.filter(folder=>folder.parent_id == folder.id);
        }
        return [];
    }
}