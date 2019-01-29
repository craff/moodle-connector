import http from "axios";
import {Mix, Selectable, Selection} from 'entcore-toolkit';

export class Folder {
    id : number;
    parentid : number;
    userid : string;
    name : string;
    structureid : string;
    nbItems: number=0;
    toJson() {
        return {
            id : this.id,
            parentid : this.parentid,
            userid : this.userid,
            name : this.name,
            structureid : this.structureid
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

}