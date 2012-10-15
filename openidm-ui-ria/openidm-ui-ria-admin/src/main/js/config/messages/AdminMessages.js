/** 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 ForgeRock AS. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

/*global define*/

/**
 * @author jdabrowski
 */
define("config/messages/AdminMessages", [
], function() {
    
    var obj = {
            //admin
            "cannotDeleteYourself": {
                msg: "You can't delete yourself",
                type: "error"
            },
            "userDeleted": {
                msg: "User has been deleted",
                type: "info"
            },
            "userDeleteError": {
                msg: "Error when deleting user",
                type: "error"
            },

            //applications
            "userApplicationsUpdate": {
                msg: "Application settings have been changed.",
                type: "info"
            },
            
            //tasks
            "completedTask": {
                msg: "Task has been completed.",
                type: "info"
            },
            "claimedTask": {
                msg: "Task has been claimed.",
                type: "info"
            },
            "unclaimedTask": {
                msg: "Task has been unclaimed.",
                type: "info"
            }
    };
    
    return obj;
});