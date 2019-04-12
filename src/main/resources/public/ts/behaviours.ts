import {Behaviours, model} from "entcore";

console.log('moodle behaviours loaded');

var moodleBehaviours = {
	resources: {
		read: {
			right: "fr-openent-moodle-controllers-MoodleController|read"
		},
		contrib: {
			right: "fr-openent-moodle-controllers-MoodleController|contrib"
		},
		manager: {
			right: "fr-openent-moodle-controllers-MoodleController|shareSubmit"
		}
	},
	workflow: {
		create: 'fr.openent.moodle.controllers.MoodleController|create',
		delete: 'fr.openent.moodle.controllers.MoodleController|delete',
		view: 'fr.openent.moodle.controllers.MoodleController|view'
	}
};


Behaviours.register('moodle', {
	behaviours: moodleBehaviours,
	dependencies: {},
	/**
	 * Allows to set rights for behaviours.
	 */
	resource : function(resource) {
		let rightsContainer = resource;

		if (resource && !resource.myRights) {
			resource.myRights = {};
		}

		for (var behaviour in moodleBehaviours.resources) {
			if (model.me.hasRight(rightsContainer, moodleBehaviours.resources[behaviour]) || model.me.userId === resource.owner.userId || model.me.userId === rightsContainer.owner.userId) {
				if (resource.myRights[behaviour] !== undefined) {
					resource.myRights[behaviour] = resource.myRights[behaviour] && moodleBehaviours.resources[behaviour];
				} else {
					resource.myRights[behaviour] = moodleBehaviours.resources[behaviour];
				}
			}
		}
		return resource;
	},

	/**
	 * Allows to define all rights to display in the share windows. Names are
	 * defined in the server part with
	 * <code>@SecuredAction(value = "xxxx.read", type = ActionType.RESOURCE)</code>
	 * without the prefix <code>xxx</code>.
	 */
	resourceRights : function() {
		return [ 'contrib', 'manager' ];
	},

	loadResources: function (callback) { }
});
