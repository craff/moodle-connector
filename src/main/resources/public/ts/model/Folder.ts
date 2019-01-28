import http from "axios";
import {Mix, Selectable, Selection} from 'entcore-toolkit';

export class Folder {
    folderid : number;
    parentid : number;
    userid : string;
    name : string;
    structureid : string;
    toJson() {
        return {
            folderid : this.folderid,
            parentid : this.parentid,
            userid : this.userid,
            name : this.name,
            structureid : this.structureid
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